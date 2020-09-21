package franz.db.services

import franz.IntegrationTest
import franz.db.BaseGleichDBTest
import franz.users.{CreateUser, Login}
import monix.execution.Scheduler.Implicits.global

trait UsersMongoTest extends BaseGleichDBTest {

  "UsersMongo" should {
    "not create users with duplicate names or emails" taggedAs(IntegrationTest) in {
      val users = UsersMongo()

      val CreateUser.Response.CreatedUser(user1) = users.createUser(CreateUser.Request(nextId(), s"${nextId()}@il.com", "pwd")).runToFuture.futureValue
      val CreateUser.Response.CreatedUser(user2) = users.createUser(CreateUser.Request(nextId(), s"${nextId()}@il.com", "pwd")).runToFuture.futureValue

      val sameUserResponse: CreateUser.Response = users.createUser(CreateUser.Request(user1.name, s"different${nextId()}@il.com", "pwd")).runSyncUnsafe(testTimeout)
      sameUserResponse shouldBe CreateUser.Response.InvalidRequest("User already exists")

      val sameEmailResponse = users.createUser(CreateUser.Request(s"different username ${nextId()}", user1.email, "pwd")).runSyncUnsafe(testTimeout)
      sameEmailResponse shouldBe CreateUser.Response.InvalidRequest("User already exists")
    }
    "be able to log in users by username or email" taggedAs(IntegrationTest) in {
      val users = UsersMongo()

      val CreateUser.Response.CreatedUser(user1) = users.createUser(CreateUser.Request(nextId(), s"${nextId()}@il.com", "pwd")).runToFuture.futureValue

      users.login(Login.Request(user1.name, "invalid password")).runToFuture.futureValue shouldBe Login.Response.empty()
      users.login(Login.Request("unknown user", "pwd")).runToFuture.futureValue shouldBe Login.Response.empty()
      val Login.Response(Some(login1)) = users.login(Login.Request(user1.name, "pwd")).runToFuture.futureValue
      val Login.Response(Some(login2)) = users.login(Login.Request(user1.email, "pwd")).runToFuture.futureValue
      login1._1 should not be login2._1
      login1._2.userId shouldBe user1.userId
      login2._2.userId shouldBe user1.userId
    }
  }
}
