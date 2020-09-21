package franz.users.routes

import cats.effect.IO
import franz.data.crud.{InsertSuccess}
import franz.data.{QueryRange, VersionedRecord}
import franz.data.VersionedRecord.syntax._
import franz.rest.Swagger4s.implicits._
import franz.rest.{BaseRouteTest, WrappedResponse}
import franz.users.Roles.RolePermissions
import franz.users.{Permissions, Roles, UserSwagger, WebUser}
import io.circe.Json
import org.http4s._

class RolesRouteTest extends BaseRouteTest {

  "RolesRoute" should {

    val Requests = UserSwagger()

    "and and list roles" in {
      Given("A roles service which a role containing a permissions users to be able to add more roles")
      val roles: Roles.Service[IO] = Roles.inMemory.rolesService
      val adminRole = RolePermissions("admin", Set(Permissions.roles.Remove, Permissions.roles.Read, Permissions.roles.Update)).versionedRecord("user")
      val InsertSuccess(0,_) = roles.updateRole(adminRole).unsafeRunSync()

      val underTest: AuthedRoutes[WebUser, IO] = RolesRoute[IO](roles)
      val client = responseForAuth(underTest, _: AuthedRequest[IO, WebUser])

      val roleRequests = Requests.roles

      def makeRequest(request: Request[IO], fromUser: WebUser): WrappedResponse = client(AuthedRequest(fromUser, request))

      def listRoles(fromUser: WebUser = testUser) = makeRequest(roleRequests.listRolesRequest(QueryRange.Default), fromUser)

      When("We list all roles")
      Then("we should see our initial role")
      val initialResponse = listRoles(userWithRoles("admin"))
      withClue(initialResponse.body) {
        initialResponse.response.status shouldBe Status.Ok
      }
      initialResponse.bodyAs[Seq[VersionedRecord[RolePermissions]]] should contain only (adminRole)


      When("We try to add a new role from a user which doesn't have our 'Permissions.roles.Update' permissions")
      val newRole = RolePermissions("foo", Set("fizz", "buzz")).versionedRecord("user")

      val failedCreateResponse = makeRequest(roleRequests.createRequest(newRole), testUser)
      Then("The request should be rejected")

      failedCreateResponse.body shouldBe "{\"requiredPermission\":\"POST:/rbac/roles\"}"
      failedCreateResponse.response.status shouldBe Status.Unauthorized
      And("The roles should be unchanged")
      roles.roles().unsafeRunSync() should contain only (adminRole)

      When("an authorized users tries to add a new role")

      val addedResponse = makeRequest(roleRequests.createRequest(newRole), userWithRoles("admin"))

      withClue(addedResponse.body) {
        addedResponse.bodyAs[InsertSuccess[Json]].newVersion shouldBe 0
        addedResponse.response.status shouldBe Status.Ok
      }

      //
      // check the updated roles
      //
      val updatedResponse = listRoles(userWithRoles("admin"))
      withClue(updatedResponse.body) {
        updatedResponse.response.status shouldBe Status.Ok
      }
      updatedResponse.bodyAs[Seq[VersionedRecord[RolePermissions]]] should contain only(newRole, adminRole)
    }

    "be able to delete routes" in {
      Given("A roles service which a role containing a permissions users to be able to add more roles")
      val roles: Roles.Service[IO] = Roles.inMemory.rolesService
      val adminRole = RolePermissions("admin", Set(Permissions.roles.Read, Permissions.roles.Update, Permissions.roles.Remove)).versionedRecord("usr")
      val InsertSuccess(0,_) = roles.updateRole(adminRole).unsafeRunSync()

      val underTest: AuthedRoutes[WebUser, IO] = RolesRoute[IO](roles)
      val client = responseForAuth(underTest, _: AuthedRequest[IO, WebUser])

      val roleRequests = Requests.roles

      def makeRequest(request: Request[IO], fromUser: WebUser): WrappedResponse = client(AuthedRequest(fromUser, request))

      def listRoles(fromUser: WebUser = testUser) = makeRequest(roleRequests.listRolesRequest((QueryRange.Default)), fromUser)

      When("We list all roles")
      Then("we should see our initial role")
      val initialResponse = listRoles(userWithRoles("admin"))
      withClue(initialResponse.body) {
        initialResponse.response.status shouldBe Status.Ok
      }
      initialResponse.bodyAs[Seq[VersionedRecord[RolePermissions]]] should contain only (adminRole)

      val deleteRequest = AuthedRequest(userWithRoles("admin"), roleRequests.removeRole(adminRole.id))
      val deleteResp = client(deleteRequest)
      deleteResp.status shouldBe Status.Ok
      val Some(deleted) = deleteResp.bodyAs[Option[VersionedRecord[RolePermissions]]]
      deleted.id shouldBe adminRole.id
    }
  }


}
