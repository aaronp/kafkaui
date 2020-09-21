package franz.users.routes

import cats.effect.IO
import franz.rest.BaseRouteTest
import franz.users.{User, UserSwagger}
import org.http4s._
import franz.rest.Swagger4s.implicits._

class UserHealthRouteTest extends BaseRouteTest {

  val Requests = UserSwagger()

  "UserHealthRoute" should {
    "echo back the user's session" in {

      val routeUnderTest = UserHealthRoute[IO]
      val request = AuthedRequest(testUser, Requests.users.status)

      val response = responseForAuth(routeUnderTest, request)
      response.bodyAs[User].name shouldBe testUser._2.name
      response.bodyAs[User].roles shouldBe testUser._2.roles

      response.status shouldBe Status.Ok
    }
  }
}
