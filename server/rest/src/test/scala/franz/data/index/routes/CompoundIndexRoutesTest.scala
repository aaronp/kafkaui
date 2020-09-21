package franz.data.index.routes

import cats.effect.IO
import franz.data.index.CompoundIndex
import franz.rest.{BaseRouteTest, RouteClient}
import franz.users.PermissionPredicate

class CompoundIndexRoutesTest extends BaseRouteTest {

  "CompoundIndexRoutes" should {
    "handle requests" in {

      val perms = PermissionPredicate.permitAll[IO]
      val routes = CompoundIndexRoutes(perms).routes

      val underTest = {
        val jsonClient = RouteClient.loggedInClient(routes).jsonClient
        CompoundIndex.client(jsonClient)
      }

      val testIO = for {
        b4 <- underTest.listIndices("foo")
        added <- underTest.addIndexToCollection("foo", CompoundIndex.expand(List("a", "b"), List("c", "d")))
        after <- underTest.listIndices("foo")
        different <- underTest.listIndices("bar")
      } yield (b4, added, after, different)

      val (Nil, added, Seq(readBack), Nil) = testIO.unsafeRunSync()
      added.isSuccess shouldBe true
      readBack shouldBe CompoundIndex.expand(List("a", "b"), List("c", "d"))
    }
  }
}
