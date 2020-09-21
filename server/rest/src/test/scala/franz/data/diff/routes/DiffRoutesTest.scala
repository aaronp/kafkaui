package franz.data.diff.routes

import cats.effect.IO
import franz.Env
import franz.data.VersionedRecord.syntax._
import franz.data.crud.CrudServicesAnyCollectionInMemory
import franz.data.diff.{Diff, DiffRest}
import franz.data.{RecordCoords, SomeTestClass}
import franz.rest.RouteClient
import franz.users.{PermissionPredicate, WebUser}
import org.http4s.AuthedRoutes
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class DiffRoutesTest extends AnyWordSpec with Matchers {
  "DiffRoutes" should {
    "support POST and GET requests" in {
      val env = Env()
      import env.implicits._
      // create our service
      val service: Diff.Service[IO] = {
        val app = CrudServicesAnyCollectionInMemory[IO](false).services
        app.insert("foo", SomeTestClass().versionedRecord(id = "A", version = 0)).unsafeRunSync()
        app.insert("foo", SomeTestClass(name = "xyz").versionedRecord(id = "A", version = 1)).unsafeRunSync()
        app.insert("foo", SomeTestClass(number = 456).versionedRecord(id = "A", version = 2)).unsafeRunSync()
        app.insert("bar", SomeTestClass(number = 456).versionedRecord(id = "B", version = 3)).unsafeRunSync()
        Diff(app.readService)
      }

      // wrap it in a route:
      val perms = PermissionPredicate.permitAll[IO]
      val underTest: AuthedRoutes[WebUser, IO] = DiffRoutes(service, perms).routes


      def verify(client: Diff.Service[IO]) = {
        val Some(diff1) = client.diff(RecordCoords("foo", "A", 0), RecordCoords("foo", "A", 1)).unsafeRunSync()
        diff1.formatted should contain allOf("data.name => [anon, xyz]", "version => [0, 1]")

        val Some(diff2) = client.diff(RecordCoords("foo", "A", 1), RecordCoords("foo", "A", 2)).unsafeRunSync()
        diff2.formatted should contain allOf("data.name => [xyz, anon]", "version => [1, 2]", "data.number => [1234, 456]")

        val Some(diff3) = client.diff(RecordCoords("foo", "A", 0), RecordCoords("bar", "B")).unsafeRunSync()
        diff3.formatted.foreach(println)
        diff3.formatted should contain allOf("version => [0, 3]", "id => [A, B]", "data.number => [1234, 456]")
      }

      // and test our client can hit our routes
      verify(DiffRest.client(RouteClient.loggedInClient(underTest).jsonClient))
      verify(DiffRest.getClient(RouteClient.loggedInClient(underTest).jsonClient))
    }
  }
}
