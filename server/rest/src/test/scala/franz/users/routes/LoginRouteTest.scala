package franz.users.routes

import cats.effect.IO
import franz.rest.BaseRouteTest
import franz.users.{Claims, JWTCache, Login, UserSwagger}
import org.http4s._
import franz.rest.Swagger4s.implicits._
import org.http4s.headers.Authorization

class LoginRouteTest extends BaseRouteTest {

  "LoginRoute" should {
    val svc = {
      def login(r : Login.Request) = r match {
        case Login.Request(user, "letmein") =>
          IO(Login.Response("abc123", Claims.forUser(user)))
        case Login.Request(_, _) =>
          IO(Login.Response.empty())
      }
      def logout(jwt : String) = IO(true)
      Login.liftF[IO](login, logout)
    }


    val Requests = UserSwagger()

    "return an auth token for valid logins" in {
      val request = Requests.login(Login.Request("foo", "letmein"))

      val cache = JWTCache.unsafe[IO]
      val routeUnderTest: HttpRoutes[IO] = LoginRoute[IO](svc, cache)

      withClue("The user should not exist before login") {
        cache.jwtCache.lookup("abc123").unsafeRunSync() shouldBe None
      }

      val response = responseFor(routeUnderTest, request)
      response.authHeader.authScheme shouldBe AuthScheme.Bearer
      response.authHeader.renderString shouldBe "Bearer abc123"

      val loginResponse = response.bodyAs[Login.Response]
      loginResponse.jwtToken shouldBe Some("abc123")
      loginResponse.user.map(_.name) shouldBe Some("foo")

      withClue("Our JWT cache should not contain the user") {
        val Some(user) = cache.jwtCache.lookup("abc123").unsafeRunSync()
        user._2.name shouldBe "foo"
      }
    }
    "NOT return an auth token for valid logins" in {
      val request = Requests.login(Login.Request("foo", "invalid"))

      val cache = JWTCache.unsafe[IO]
      val routeUnderTest: HttpRoutes[IO] = LoginRoute[IO](svc, cache)

      val response = responseFor(routeUnderTest, request)

      response.response.headers.get(Authorization) shouldBe None

      withClue("Our JWT cache should NOT contain the user") {
        cache.jwtCache.lookup("abc123").unsafeRunSync() shouldBe None
      }

      response.body shouldBe """{"tokenAndUser":null}"""
    }
  }
}
