package franz.users

import cats.effect.IO
import franz.data.VersionedRecord
import franz.users.UserRoles.SetUserRolesResponse
import io.circe.syntax._

class UserRolesTest extends BaseUsersTest {

  "UserRoles.associateUser" should {
    "be able to associate a user with a role" in {
      val userRoles: UserRoles.Service[IO] = UserRoles.empty[IO].unsafeRunSync()
      userRoles.roleRecordsForUser("test user").unsafeRunSync() shouldBe None
      userRoles.associateUser("test user", Set("foo", "bar")).unsafeRunSync() shouldBe SetUserRolesResponse(Right(0))

      val Some(VersionedRecord(readBack,_, _,  _, _)) = userRoles.roleRecordsForUser("test user").unsafeRunSync()
      readBack should contain only("foo", "bar")
    }
    "not update user roles if the version is out-of-date" in {
      val userRoles: UserRoles.Service[IO] = UserRoles.empty[IO].unsafeRunSync()

      userRoles.associateUser("meh", Set("foo", "bar"), 10).unsafeRunSync() shouldBe SetUserRolesResponse(Right(10))
      userRoles.associateUser("meh", Set("new perms"), 9).unsafeRunSync() shouldBe SetUserRolesResponse(Left("out-of-date version: 10 is more recent than 9"))
      userRoles.associateUser("meh", Set("new perms"), 11).unsafeRunSync() shouldBe SetUserRolesResponse(Right(11))
      userRoles.associateUser("different user", Set("new perms"), 9).unsafeRunSync() shouldBe SetUserRolesResponse(Right(9))

      val Some(VersionedRecord(readBack,_, _,  _, _)) = userRoles.roleRecordsForUser("meh").unsafeRunSync()
      readBack should contain only ("new perms")
    }
  }
  "UserRoles.SetUserRolesResponse" should {
    "serialize successful values to and from json" in {
      val x = UserRoles.SetUserRolesResponse(1)
      x.asJson.as[UserRoles.SetUserRolesResponse] shouldBe Right(x)
    }
    "serialize error values to and from json" in {
      val x = UserRoles.SetUserRolesResponse("bang")
      x.asJson.as[UserRoles.SetUserRolesResponse] shouldBe Right(x)
    }
  }
}
