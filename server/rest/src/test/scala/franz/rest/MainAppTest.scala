package franz.rest

import franz.data.crud.{InsertRecord, InsertSuccess}
import franz.users.UserRoles.SetUserRolesResponse
import franz.users.{CreateUser, Permissions, User}

class MainAppTest extends BaseRouteTest {

  "RestApp.login" should {
    "pick up the user's roles when logging in" in {
      val client: MainAppSetup = MainAppSetup()

      // create a role which allows the user to create roles
      val InsertSuccess(0,_) = {
        import client.server.services.adminServices.rolesService
        val adminRole = rolesService.mkRoles("admin", Set(Permissions.roles.Update,
          Permissions.userRoles.canAssign("admin", "reader")))
        rolesService.updateRole(adminRole).unsafeRunSync()
      }


      val CreateUser.Response.CreatedUser(user1) = client.createUser("dave").unsafeRunSync()
      val loginResponse = client.login("dave").unsafeRunSync()

      loginResponse.jwtToken should not be (empty)

      import client.server.services.userServices.jwt
      val Some(foundUserOnServer) = jwt.lookup(loginResponse.jwtToken.get).unsafeRunSync()
      foundUserOnServer._2.name shouldBe "dave"
      foundUserOnServer._2.roles shouldBe empty

      // update our server to permit this user to be able to grant user roles
      jwt.set(loginResponse.jwtToken.get, foundUserOnServer._2.setRoles("admin")).unsafeRunSync()


      val SetUserRolesResponse(Right(_)) = client.grantUserRoles(user1.userId, "admin", "reader").unsafeRunSync()

      val updatedUser: User = client.healthClient.userStatus.unsafeRunSync()
      val Some(originalUser) = loginResponse.user
      originalUser.roles shouldBe (empty)
      updatedUser.roles shouldBe Set("admin", "reader")
    }

    "not allow unauthorized users to assign roles" in {
      val client: MainAppSetup = MainAppSetup()
      import client.server.services.userServices.jwt
      // create a role which allows the user to sort out roles
      client.createRole("admin", Permissions.roles.Update).unsafeRunSync()

      val CreateUser.Response.CreatedUser(user1) = client.createUser("dave").unsafeRunSync()
      val loginResponse = client.login("dave").unsafeRunSync()

      loginResponse.jwtToken should not be (empty)
      val Some(foundUserOnServer) = jwt.lookup(loginResponse.jwtToken.get).unsafeRunSync()
      foundUserOnServer._2.name shouldBe "dave"

      val err = intercept[IllegalArgumentException] {
        client.grantUserRoles(user1.userId, "admin", "reader", "foo").unsafeRunSync()
      }
      err.getMessage should include("Status was 401 Unauthorized: {\"requiredPermission\":\"POST:/rbac/userrole\"}")
    }
  }
  "RestApp.create user" should {
    "be able to create a new user and log in with 'em" in {
      val client = MainAppSetup()

      val loginTokenTest = for {
        CreateUser.Response.CreatedUser(user1) <- client.createUser("user1")
        loginResponse <- client.login("user1")
      } yield {
        user1.name shouldBe "user1"
        user1.email shouldBe ("user1")
        loginResponse.user.map(_.name) shouldBe Some("user1")
        loginResponse.jwtToken
      }

      loginTokenTest.unsafeRunSync() should not be empty
    }
  }

  "RestApp.logout" should {
    "not be able to access services after a user has logged out" in {
      val client = MainAppSetup()

      val loginTokenTest = for {
        CreateUser.Response.CreatedUser(user1) <- client.createUser("user1")

        loginResponse <- client.login("user1")
      } yield {
        user1.name shouldBe "user1"
        user1.email shouldBe ("user1")
        loginResponse.user.map(_.name) shouldBe Some("user1")
        loginResponse.jwtToken
      }
      loginTokenTest.unsafeRunSync() should not be empty
    }
  }
}
