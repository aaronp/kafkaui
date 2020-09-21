package franz.users

import cats.effect.IO

class JWTCacheTest extends BaseUsersTest {

  "JWTCache.logout" should {
    "remove the user" in {
      val cache = JWTCache.unsafe[IO].jwtCache

      val program = for {
        _ <- cache.set("token1", Claims.forUser("dave"))
        _ <- cache.set("token2", Claims.forUser("susan"))
        foundDave <- cache.lookup("token1")
        foundSusan <- cache.lookup("token2")
        missing <- cache.lookup("token3")
        _ <- cache.logout("token1")
        shouldNotFindDave <- cache.lookup("token1")
      } yield {
        (foundDave, foundSusan, missing, shouldNotFindDave)
      }
      val (Some(dave1), Some(susan1), None, None) = program.unsafeRunSync()
      dave1._2.name shouldBe "dave"
      susan1._2.name shouldBe "susan"
    }
  }
}
