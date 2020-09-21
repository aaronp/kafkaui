package franz.rest

import cats.effect.IO
import com.typesafe.config.{Config, ConfigFactory}
import franz.users.Roles.UpdateResponse
import franz.{Env, UserApi}
import franz.users._
import io.circe.Json
import org.http4s.{Response, _}

/**
 * A wrapper for tests which holds both the client and server implementations of services
 *
 */
class MainAppSetup(val server: RestApp[IO], defaultPassword: String = "pwd") {

  val app: HttpApp[IO] = server.app

  val testClient: Swagger.Client[IO, Json] = RouteClient.forApp(app).jsonClient
  val apiClient: UserApi[IO] = UserApi.client(testClient)
  val adminClient: AdminApi[IO] = AdminApi.client(testClient)
  val healthClient: UserHealth.Client[IO, Json] = UserHealth.Client(testClient)

  def grantUserRoles(userId: String, firstRole: String, theRest: String*): IO[UserRoles.SetUserRolesResponse] = {
    adminClient.userRoles.associateUser(userId, theRest.toSet + firstRole)
  }

  def createRole(roleName: String, firstPermission: String, theRest: String*): IO[UpdateResponse] = {
    import server.services.adminServices.rolesService
    val roles = rolesService.mkRoles(roleName, theRest.toSet + firstPermission)
    rolesService.updateRole(roles)
  }

  def login(user: String, pwd: String = defaultPassword): IO[Login.Response] = {
    apiClient.loginService.login(Login.Request(user, pwd))
  }

  def createUser(email: String, pwd: String = defaultPassword): IO[CreateUser.Response] = {
    apiClient.createUserService.createUser(CreateUser.Request(email, email, pwd))
  }
}

object MainAppSetup {
  def loginClient(restClient: Swagger.Client[IO, Response[IO]]): Login.Client[IO, Response[IO]] = {
    Login.Client(restClient, franz.rest.parserFor[IO, Login.Response], franz.rest.parserFor[IO, Boolean])
  }


  def apply(config: Config = ConfigFactory.load()): MainAppSetup = {
    val env = Env()
    import env._

    val restAppF = RestApp[IO](config)(shift, ctxtShift, parallel)
    val app = restAppF.unsafeRunSync()
    new MainAppSetup(app)
  }

}
