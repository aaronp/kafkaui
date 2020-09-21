package franz.firestore.services

import java.util.UUID

import com.typesafe.config.ConfigFactory
import franz.firestore.{FS, FSEnv}
import franz.jwt.{Hmac256, PasswordHash}
import franz.users.{CreateUser, Login}
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class UsersFirestoreTest extends AnyWordSpec with Matchers with Eventually {

  val rt: zio.Runtime[zio.ZEnv] = zio.Runtime.default
  val live = FSEnv.live

  implicit def asRichZIO[A](zio: FS[A]) = new {
    def value: A = rt.unsafeRun(zio.provideLayer(live))
  }


  "UsersFirestore" should {
    "create and log users in" in {
      val config = ConfigFactory.load()
      val hasher = PasswordHash(config)
      val sEcr3t = Hmac256.asSecret("don't tell")
      val underTest = UsersFirestore(sEcr3t, hasher)

      val userName = s"UsersFirestoreTest${UUID.randomUUID().toString}"
      val newEmail = s"$userName@example.com"

      val CreateUser.Response.CreatedUser(user) = underTest.createUser(CreateUser.Request(userName, newEmail, "my password")).value

      val Some(loggedInUserByName) = underTest.login(Login.Request(userName, "my password")).value.user
      loggedInUserByName.name shouldBe user.name
      loggedInUserByName.name shouldBe userName
      loggedInUserByName.email shouldBe newEmail
      val Some(loggedInUserByEmail) = underTest.login(Login.Request(newEmail, "my password")).value.user
      loggedInUserByEmail.name shouldBe user.name
      loggedInUserByEmail.email shouldBe newEmail
      loggedInUserByEmail.userId should not be (null)

      underTest.login(Login.Request(newEmail, "misspelled")).value.user shouldBe None
      underTest.login(Login.Request("Who?", "my password")).value.user shouldBe None
    }
  }
}
