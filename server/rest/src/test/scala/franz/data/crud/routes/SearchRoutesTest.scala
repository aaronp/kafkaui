package franz.data.crud.routes

import cats.effect.IO
import franz.data.QueryRange
import franz.data.crud.Search
import franz.rest.{BaseRouteTest, RouteClient}
import franz.users.PermissionPredicate

import scala.collection.mutable.ListBuffer

class SearchRoutesTest extends BaseRouteTest {

  "SearchRoutes" should {
    "handle POST requests" in {

      val requests = ListBuffer[Search.Request]()
      val fakeResponse = Search.Response(3, Nil, 4)
      val service = Search.liftF[IO] { request =>
        requests += request
        IO.pure(fakeResponse)
      }

      val perms = PermissionPredicate.permitAll[IO]
      val underTest = SearchRoutes(service, perms)
      val testClient = Search.client(RouteClient.loggedInClient(underTest).jsonClient)

      val clientRequest = Search.Request("collection name", "some criteria", QueryRange(3, 4))
      testClient.search(clientRequest).unsafeRunSync() shouldBe fakeResponse
      requests should contain only (clientRequest)
    }
  }
}
