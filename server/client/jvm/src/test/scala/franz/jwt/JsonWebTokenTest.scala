package franz.jwt

import franz.users.Claims
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class JsonWebTokenTest extends AnyWordSpec with Matchers {

  "JsonWebToken" should {
    "create and validate an Hs256 token from claims and a secret" in {
      val user = Claims(name = "Alice")
      val token = JsonWebToken.asHmac256Token(user, "S3cr3t")
      token.count(_ == '.') shouldBe 2

      val Right(parsed) = JsonWebToken.parseToken(token)
      parsed.isValidForSecret("S3cr3t") shouldBe true
      parsed.isValidForSecret("S3cr3t!") shouldBe false
      parsed.isValidForSecret("wrong!") shouldBe false
      parsed.isHs256 shouldBe true

      val Right(readBack) = JsonWebToken.forToken(token, Hmac256.asSecret("S3cr3t"))
      readBack.claims shouldBe user
    }
  }
}
