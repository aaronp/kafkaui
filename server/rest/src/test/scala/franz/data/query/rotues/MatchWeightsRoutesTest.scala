package franz.data.query.rotues

import cats.effect.IO
import franz.data.RecordCoords
import franz.data.VersionedRecord.syntax._
import franz.data.crud.CrudServices
import franz.data.crud.routes.CrudRoutes
import franz.data.index.MatchWeights
import franz.rest.RouteClient
import franz.users.PermissionPredicate
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class MatchWeightsRoutesTest extends AnyWordSpec with Matchers {
  "MatchWeights" should {
    "read, write and delete MatchWeights" in {
      val client: CrudServices[IO, MatchWeights] = {
        val perms = PermissionPredicate.permitAll[IO]
        val underTest: CrudRoutes[IO, MatchWeights] = MatchWeightsRoutes[IO](perms)
        val restClient: RouteClient[IO] = RouteClient.loggedInClient(underTest.routes)
        MatchWeights.client(restClient.jsonClient)
      }

      client.insert(MatchWeights.of("a" -> 1).versionedRecord(id = "1")).unsafeRunSync().isSuccess shouldBe true
      client.insert(MatchWeights.of("b" -> 2).versionedRecord(id = "1", version = 1)).unsafeRunSync().isSuccess shouldBe true
      val Some(read) = client.readService.read(RecordCoords("ignored", "1")).unsafeRunSync()
      val Some(deleted) = client.deleteService.delete("1").unsafeRunSync()
      deleted.version shouldBe 1
      deleted shouldBe read
      val readAfterDelete = client.readService.read(RecordCoords("ignored", "1")).unsafeRunSync()
      readAfterDelete shouldBe None
    }
  }
}
