package franz.users

import java.time.{ZoneId, ZoneOffset, ZonedDateTime}

import scala.concurrent.duration._

class ClaimsTest extends BaseUsersTest {

  "Claims.sessionDuration" should {
    "should return the length of the user session" in {

      val issued = ZonedDateTime.of(2020, 1, 1, 1, 1, 1, 1, ZoneOffset.UTC)
      val user = Claims.forUser("example", issued)
      user.sessionDuration shouldBe None

      val inThreeMinutes = issued.plusMinutes(3)
      user.withExpiry(inThreeMinutes).sessionDuration shouldBe Some(3.minutes)
    }
  }

  "Claims.isExpired" should {
    "return true when a token expires" in {
      val now = ZonedDateTime.of(2019, 1, 2, 3, 4, 5, 6, ZoneId.of("UTC"))
      val claims = Claims.after(10.seconds, now).forUser("somebody")
      claims.isExpired(now) shouldBe false
      claims.isExpired(now.plusSeconds(9)) shouldBe false
      claims.isExpired(now.plusSeconds(10)) shouldBe true
      claims.isExpired(now.plusSeconds(11)) shouldBe true

      Claims(name = "never expires").isExpired(now) shouldBe false
    }
  }
  "Claims json" should {
    "encode to and from json" in {
      val now = ZonedDateTime.now()
      val bc = Claims.after(10.seconds, now).forUser("Dave").setRoles("a", "another role").setRoles("whatever", "show me; don't tell me")
      val json = bc.toJson
      Claims.fromJson(json) shouldBe Right(bc)
    }
  }

}
