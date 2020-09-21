package franz.rest

import cats.effect.IO
import franz.users.{Claims, JWT, WebUser}
import org.http4s._
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

abstract class BaseRouteTest extends AnyWordSpec with Matchers with GivenWhenThen {

  def testUser: WebUser = {
    "a.b.c" -> Claims.forUser("dave").withId("testUserId")
  }

  def userWithRoles(roles: String*): (JWT, Claims) = {
    val (a, u) = testUser
    (a, u.setRoles(roles.toSet))
  }

  def responseFor(routeUnderTest: HttpRoutes[IO], request: Request[IO]): WrappedResponse = {
    WrappedResponse(routeUnderTest, request)
  }
  def unsafeResponseForAuth(routeUnderTest: AuthedRoutes[WebUser, IO], request: AuthedRequest[IO, WebUser]): Option[Response[IO]] = {
    routeUnderTest.run(request).value.unsafeRunSync()
  }
  def responseForAuth(routeUnderTest: AuthedRoutes[WebUser, IO], request: AuthedRequest[IO, WebUser]): WrappedResponse = {
    WrappedResponse(unsafeResponseForAuth(routeUnderTest, request).get)
  }
  def responseForAuth(routeUnderTest: AuthedRoutes[WebUser, IO], request: Request[IO], user : WebUser = testUser): WrappedResponse = {
    responseForAuth(routeUnderTest, AuthedRequest(user, request))
  }
}

