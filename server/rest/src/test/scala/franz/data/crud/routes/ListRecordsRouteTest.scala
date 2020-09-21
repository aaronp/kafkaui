package franz.data.crud.routes

import cats.effect.IO
import franz.data.VersionedRecord.syntax._
import franz.data.crud.ListRecords
import franz.rest.BaseRouteTest
import franz.users.Roles
import franz.users.Roles.RolePermissions
import org.http4s.implicits._
import org.http4s.{Method, Request, Status}

class ListRecordsRouteTest extends BaseRouteTest {

  "ListRecordsRoute" should {
    "handle GET requests" in {

      val service = ListRecords.lift[IO, Seq[Int]] { range =>
        range.fromIterable(Seq(1, 2, 3)).toSeq
      }

      val roles = Roles.inMemory
      roles.updateRole(RolePermissions("meh", Set("GET:/foo")).versionedRecord("user")).unsafeRunSync()
      val underTest = ListRecordsRoute.single("foo", service, roles)

      val r = Request[IO](method = Method.GET, uri = uri"/foo")
      val response = responseForAuth(underTest, r, userWithRoles("meh"))
      response.status shouldBe Status.Ok
      response.body shouldBe """[1,2,3]"""

    }
  }
}
