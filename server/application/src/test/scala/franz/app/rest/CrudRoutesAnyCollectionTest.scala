package franz.app.rest

import cats.effect.IO
import franz.data.RecordCoords
import franz.data.VersionedRecord.syntax._
import franz.data.crud.routes.CrudRoutesAnyCollection
import franz.data.crud.{CrudServicesAnyCollection, CrudServicesAnyCollectionInMemory}
import franz.rest.RouteClient
import franz.users.PermissionPredicate
import io.circe.syntax._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class CrudRoutesAnyCollectionTest extends AnyWordSpec with Matchers {

  "CrudRoutes.client" should {
    "be able to invoke all the application services via REST" in {
      val setup = CrudRoutesAnyCollectionTest.Setup()
      import setup._

      val (inserts, Some(readLatest), Some(readV1), Some(readPreviousV1), collections, Some(readDeletedBefore), None) = test.unsafeRunSync()
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

    }
  }
}

object CrudRoutesAnyCollectionTest {

  case class Setup() {
    val app = CrudServicesAnyCollectionInMemory[IO](false)
    val perms = PermissionPredicate.permitAll[IO]
    val underTest = CrudRoutesAnyCollection("foo", app.services, perms)
    val client: RouteClient[IO] = RouteClient.loggedInClient(underTest.routes)
    val appClient: CrudServicesAnyCollection[IO] = CrudServicesAnyCollection.client("foo", client.jsonClient)


    val test = for {
      a <- appClient.insert("dave", "test1".asJson.versionedRecord(id = "1"))
      b <- appClient.insert("sue", "test2".asJson.versionedRecord(id = "2"))
      c <- appClient.insert("dave", "test1".asJson.versionedRecord(id = "3", version = 1))
      c2 <- appClient.insert("dave", "updated".asJson.versionedRecord(id = "3", version = 2))
      _ <- appClient.insert("dave", "ten".asJson.versionedRecord(id = "3", version = 10))
      readLatest <- appClient.readService.read(RecordCoords("dave", "3"))
      readV1 <- appClient.readService.read(RecordCoords("dave", "3", 1))
      readPreviousV1 <- appClient.readService.read(RecordCoords.previous("dave", "3", 50))
      _ <- appClient.insert("foo", "bar".asJson.versionedRecord(id = "1"))
      _ <- appClient.insert("foo", "delete me".asJson.versionedRecord(id = "2"))
      readDeletedBefore <- appClient.readService.read(RecordCoords("foo", "2"))
      _ <- appClient.deleteService.delete("foo" -> "2")
      readDeletedAfter <- appClient.readService.read(RecordCoords("foo", "2"))
      collections <- appClient.listService.list(0, 10)
    } yield (List(a, b, c, c2), readLatest, readV1, readPreviousV1, collections, readDeletedBefore, readDeletedAfter)
  }

}
