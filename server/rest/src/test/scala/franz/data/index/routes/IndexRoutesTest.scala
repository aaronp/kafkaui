package franz.data.index.routes


import cats.effect.IO
import franz.DataNamespace
import franz.data.RecordCoords
import franz.data.VersionedRecord.syntax._
import franz.data.index.{AssociationQueries, FixedReferences, IndexingCrud}
import franz.rest.RouteClient
import franz.users.PermissionPredicate
import io.circe.syntax._
import cats.implicits._
import franz.Env
import franz.data.crud.CrudServicesAnyCollection
import franz.data.crud.routes.CrudRoutesAnyCollection
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec


class IndexRoutesTest extends AnyWordSpec with Matchers {

  "IndexRoutes.client" should {
    "be able to invoke all the application services via REST" in {
      val env = Env()
      import env.implicits._

      val indexSetup = IndexingCrud.inMemory[IO](false, 10)
      val indexRest = indexSetup.associationQueries


      val perms = PermissionPredicate.permitAll[IO]
      val underTest = IndexRoutes(indexRest, perms)
      val client: RouteClient[IO] = RouteClient.loggedInClient(underTest.routes <+> CrudRoutesAnyCollection(DataNamespace, indexSetup.indexingCrud, perms).routes)
      val crudClient: CrudServicesAnyCollection[IO] = CrudServicesAnyCollection.client(DataNamespace, client.jsonClient)
      val indexClient: AssociationQueries[IO] = AssociationQueries.client(client.jsonClient)


      val test = for {
        a <- crudClient.insert("dave", "test1".asJson.versionedRecord(id = "1"))
        b <- crudClient.insert("sue", "test2".asJson.versionedRecord(id = "2"))
        c <- crudClient.insert("dave", "test1".asJson.versionedRecord(id = "3", version = 1))
        c2 <- crudClient.insert("dave", "updated".asJson.versionedRecord(id = "3", version = 2))
        _ <- crudClient.insert("dave", "ten".asJson.versionedRecord(id = "3", version = 10))
        readLatest <- crudClient.readService.read(RecordCoords("dave", "3"))
        readV1 <- crudClient.readService.read(RecordCoords("dave", "3", 1))
        readPreviousV1 <- crudClient.readService.read(RecordCoords.previous("dave", "3", 50))
        _ <- crudClient.insert("foo", "bar".asJson.versionedRecord(id = "1"))
        _ <- crudClient.insert("foo", "delete me".asJson.versionedRecord(id = "2"))
        readDeletedBefore <- crudClient.readService.read(RecordCoords("foo", "2"))
        _ <- crudClient.deleteService.delete("foo" -> "2")
        readDeletedAfter <- crudClient.readService.read(RecordCoords("foo", "2"))
        collections <- crudClient.listService.list(0, 10)
        test1Index <- indexClient.readIndex.read("test1")
        deleteMeIndex <- indexClient.readIndex.read("delete me")
        barIndex <- indexClient.readIndex.read("bar")
      } yield (List(a, b, c, c2), readLatest, readV1, readPreviousV1, collections, readDeletedBefore, readDeletedAfter, List(test1Index, deleteMeIndex, barIndex))

      val (inserts, Some(readLatest), Some(readV1), Some(readPreviousV1), collections, Some(readDeletedBefore), None, indices) = test.unsafeRunSync()
      inserts.foreach(_.isSuccess shouldBe true)
      collections should contain only("dave", "sue", "foo")
      readV1.version shouldBe 1
      readV1.data shouldBe "test1".asJson
      readPreviousV1.version shouldBe 10
      readPreviousV1.data shouldBe "ten".asJson
      readLatest.version shouldBe 10
      readLatest.data shouldBe "ten".asJson
      readDeletedBefore.version shouldBe 0
      readDeletedBefore.data shouldBe "delete me".asJson

      val List(Some(FixedReferences(idx1)), Some(empty), Some(FixedReferences(idx3))) = indices
      empty.isEmpty shouldBe true
      idx1.head.collection shouldBe "dave"
      idx3.head.collection shouldBe "foo"
    }
  }
}
