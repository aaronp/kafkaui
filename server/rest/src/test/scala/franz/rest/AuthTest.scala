package franz.rest

import java.time.{ZoneOffset, ZonedDateTime}

import cats.effect.IO
import franz.jwt.Hmac256
import franz.rest.Swagger4s.implicits._
import franz.users.RichClaims._
import franz.users._
import franz.users.routes.{JsonWebToken, UserHealthRoute}
import org.http4s.{AuthedRoutes, Status}

import scala.concurrent.duration._

class AuthTest extends BaseRouteTest {

  "Auth.withRefreshedTokens" should {

    "set an updated token with an extended expiry for successful requests" in {
      val someWrappedAuthRoute: AuthedRoutes[(JsonWebToken, Claims), IO] = UserHealthRoute[IO]
      val cache = JWTCache.unsafe[IO]
      val seed = Hmac256.asSecret("secret")
      val userRoles = UserRoles.empty[IO].unsafeRunSync()

      val underTest = Auth.withRefreshedTokens(someWrappedAuthRoute, cache.jwtCache, userRoles, seed, 2.seconds)

      val now = ZonedDateTime.of(2020, 1, 1, 1, 1, 1, 1, ZoneOffset.UTC)

      // setup a user and 'log them in' to our JWT service
      val user = Claims.forUser("carl").incExpiry(1.minute, now)
      val originalJWT: String = user.asToken(seed)

      cache.jwtCache.set(originalJWT, user).unsafeRunSync()
      val request = UserSwagger().users.status.toHttp4s[IO](originalJWT)

      val response = responseFor(underTest, request)
      response.status shouldBe Status.Ok
      response.bodyAs[User] shouldBe user

      response.authToken.get should not be (originalJWT)
      val Right(newUserFromToken) = RichClaims.forJWT(response.authToken.get, seed)
      withClue("The issued time should not have been changed") {
        user.issuedAt should not be empty
        newUserFromToken.issuedAt shouldBe user.issuedAt
      }

      // let's not do this - it makes it really hard if the UI hits and error and doesn't have the absolute latest token
      //      withClue("The old token should be invalid") {
      //        cache.jwtCache.lookup(originalJWT).unsafeRunSync() shouldBe None
      //        cache.jwtCache.lookup(response.authToken.get).unsafeRunSync() should not be None
      //      }
    }
    "not set a token in the response if one isn't set" in {
      val someWrappedAuthRoute: AuthedRoutes[(JsonWebToken, Claims), IO] = UserHealthRoute[IO]
      val cache = JWTCache.unsafe[IO]
      val userRoles = UserRoles.empty[IO].unsafeRunSync()
      val seed = Hmac256.asSecret("secret")

      val underTest = Auth.withRefreshedTokens(someWrappedAuthRoute, cache.jwtCache, userRoles, seed, 2.seconds)

      val request = UserSwagger().users.status.toHttp4s[IO]
      val response = responseFor(underTest, request)
      response.status shouldBe Status.Forbidden
      response.authOpt shouldBe None
    }
  }
}
