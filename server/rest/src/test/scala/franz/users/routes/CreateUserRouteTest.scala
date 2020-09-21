package franz.users.routes

import cats.effect.IO
import franz.jwt.Hmac256
import franz.rest.BaseRouteTest
import franz.rest.Swagger4s.implicits._
import franz.users.{CreateUser, CreateUserInMemory, JWTCache, UserSwagger}
import org.http4s.{HttpRoutes, Status}

class CreateUserRouteTest extends BaseRouteTest {

  "CreateUserRoute" should {
    "Create users" in {
      val cache = JWTCache.empty[IO].unsafeRunSync()
      val service = CreateUserInMemory.empty[IO](cache, Hmac256.asSecret("test")).unsafeRunSync()
      val routeUnderTest: HttpRoutes[IO] = CreateUserRoute[IO](service.createUserService)

      val Requests = UserSwagger().users
      val response = responseFor(routeUnderTest, Requests.createRequest("foo", "bar"))
      response.status shouldBe Status.Ok
      response.bodyAs[CreateUser.Response.CreatedUser].user.name shouldBe "foo"

      val alreadyExistsResponse = responseFor(routeUnderTest, Requests.createRequest("foo", "bar"))
      alreadyExistsResponse.status should not be Status.Ok
      alreadyExistsResponse.body shouldBe """{"error":"User already exists"}"""

      val newUser2 = responseFor(routeUnderTest, Requests.createRequest("different", "bar"))
      newUser2.bodyAs[CreateUser.Response.CreatedUser].user.name shouldBe "different"
    }
  }
}
