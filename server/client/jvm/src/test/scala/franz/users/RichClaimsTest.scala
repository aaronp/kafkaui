package franz.users

import java.time.{ZoneOffset, ZonedDateTime}

import cats.effect.IO
import franz.jwt.Hmac256
import franz.users.RichClaims._
import org.scalatest.GivenWhenThen

import scala.concurrent.duration._

class RichClaimsTest extends BaseUsersTest with GivenWhenThen {

  val secret = Hmac256.asSecret("test")

  "RichClaims.refresh" should {
    "update the user's roles and session" in {


      Given("An initial user with no roles or session expiry")
      val cache = JWTCache.unsafe[IO].jwtCache
      val initialUser = Claims.forUser("foo").withId("123")
      initialUser.expiresAt shouldBe None
      initialUser.roles shouldBe (empty)


      And("We set some roles in a user roles service")
      val roles = UserRoles.empty[IO].unsafeRunSync()
      roles.associateUser("123", Set("a", "b", "C")).unsafeRunSync().isSuccess shouldBe true

      And("Log the user in to the jwt cache")
      cache.set("a.b.c", initialUser).unsafeRunSync()


      When("we refresh the user with our services")
      val now = ZonedDateTime.of(2020, 1, 1, 1, 1, 1, 1, ZoneOffset.UTC)
      val (newToken, newUser) = initialUser.refresh("a.b.c", cache, roles, 1.minute, secret, now).unsafeRunSync()

      Then("The updated user should have roles and session set, and the services should be updated")
      newUser.roles shouldBe Set("a", "b", "C")
      newUser.expiresAt.map(_.toEpochSecond) shouldBe Some(now.plusMinutes(1).toEpochSecond)
      cache.lookup(newToken).unsafeRunSync() shouldBe Some((newToken, newUser))

      // let's not do auto-invalidate/logout:
      //cache.lookup("a.b.c").unsafeRunSync() shouldBe None
    }
  }
}
