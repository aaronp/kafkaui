package franz.data.crud
package routes

import cats.effect.IO
import franz.data.VersionedRecord.syntax._
import franz.data.{CollectionName, VersionedJson, VersionedJsonResponse}
import franz.rest.{RouteClient, BaseRouteTest, Swagger}
import franz.users.Roles.RolePermissions
import franz.users._
import franz.users.routes.JsonWebToken
import io.circe.Json
import io.circe.syntax._
import org.http4s.circe._
import org.http4s.implicits._
import org.http4s.{AuthedRoutes, Method, Request, Status}

import scala.collection.mutable.ListBuffer

class InsertRecordRouteTest extends BaseRouteTest {

  "InsertRecordRoute" should {
    "handle POST requests" in {

      val service = InsertRecord.lift[IO, Json, VersionedJsonResponse] { in: Json =>
        InsertResponse.inserted(0, in.versionedRecord())
      }.insertService

      val roles = Roles.inMemory.rolesService
      roles.updateRole(RolePermissions("meh", Set("POST:/foo")).versionedRecord("foo")).unsafeRunSync()
      val underTest: AuthedRoutes[(JsonWebToken, Claims), IO] = InsertRecordRoute.single("foo", InsertRecord.ignoringUser(service).insertService, roles)

      val data = Json.obj("hi" -> Json.fromString("there"))
      val r = Request[IO](method = Method.POST, uri = uri"/foo").withEntity(data)
      val response = responseForAuth(underTest, r, userWithRoles("meh"))
      response.status shouldBe Status.Ok

      response.bodyAs[InsertSuccess[VersionedJson]].newValue.data shouldBe data
      response.bodyAs[InsertSuccess[VersionedJson]].newVersion shouldBe 0
    }
  }
}
