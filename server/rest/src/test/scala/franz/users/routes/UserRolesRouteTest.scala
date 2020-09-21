package franz.users.routes

import cats.effect.IO
import cats.syntax.either._
import franz.data.VersionedRecord.syntax._
import franz.data.{Versioned, VersionedRecord}
import franz.rest.{BaseRouteTest, WrappedResponse}
import franz.rest.Swagger4s.implicits._
import franz.users.Roles.RolePermissions
import franz.users.UserRoles.{AssociateRolesWithUser, SetUserRolesResponse}
import franz.users.{Permissions, Roles, UserRoles, UserSwagger, WebUser}
import org.http4s._

class UserRolesRouteTest extends BaseRouteTest {

  "UserRolesRoute" should {

    val Requests = UserSwagger().userRoles

    "be able to associate users with a particular role" in {
      val service = UserRoles.empty[IO].unsafeRunSync()
      val roles = Roles.inMemory.rolesService
      roles.updateRole(RolePermissions("test role", Set(
        Permissions.userRoles.canAssign("roleA", "roleB"),
        Permissions.userRoles.canList("meh"))
      ).versionedRecord("user")).unsafeRunSync()

      val underTest = UserRolesRoute(roles, service)

      val client = responseForAuth(underTest, _: AuthedRequest[IO, WebUser])

      def setRoles(setRequest: VersionedRecord[AssociateRolesWithUser], fromUser: WebUser = testUser) = {
        val request = AuthedRequest(fromUser, Requests.associateRoles(setRequest))
        client(request)
      }

      def listRoles(userId: String, fromUser: WebUser = testUser) = {
        val request = AuthedRequest(fromUser, Requests.listRoles(userId))
        client(request)
      }

      val setRequest = AssociateRolesWithUser("meh", Set("roleA", "roleB")).versionedRecord("foo", version = 1)
      val firstResponse = setRoles(setRequest, userWithRoles("test role"))
      firstResponse.status shouldBe Status.Ok
      firstResponse.bodyAs[SetUserRolesResponse] shouldBe SetUserRolesResponse(1.asRight)


      val staleResult = setRoles(setRequest.withVersion(0), userWithRoles("test role"))
      staleResult.status should not be Status.Ok
      staleResult.bodyAs[SetUserRolesResponse] shouldBe SetUserRolesResponse(Left("out-of-date version: 1 is more recent than 0"))

      val readBack: WrappedResponse = listRoles("meh", userWithRoles("test role"))

      import Versioned.syntax._
      readBack.bodyAs[Versioned[Set[String]]] shouldBe Set("roleA", "roleB").versioned(version = 1)
      readBack.status shouldBe Status.Ok
    }
  }

}
