package franz.db.services

import com.typesafe.config.{Config, ConfigFactory}
import franz.db.impl.{CrudServicesAnyCollectionMongo, VersionedRecordsMongo}
import franz.jwt.PasswordHash
import franz.users._
import franz.{GleichApiJVM, UserApi, users}
import javax.crypto.spec.SecretKeySpec
import monix.eval.Task
import org.mongodb.scala.model.Filters

object UsersMongo {

  def apply(config: Config = ConfigFactory.load()): UsersMongo = Init(config).usersMongo

  def apply(database: VersionedRecordsMongo, jwtSeed: SecretKeySpec, hasher: PasswordHash): UsersMongo = {
    new UsersMongo(database, jwtSeed, hasher)
  }

  case class Init(database: VersionedRecordsMongo, jwtSeed: SecretKeySpec, hasher: PasswordHash) {
    def usersMongo = UsersMongo(database, jwtSeed, hasher)
  }

  object Init {
    def apply(config: Config = ConfigFactory.load()) = {
      new Init(
        database = VersionedRecordsMongo(config),
        jwtSeed = GleichApiJVM.seedForConfig(config),
        hasher = PasswordHash(config)
      )
    }
  }
}

class UsersMongo(val database: VersionedRecordsMongo, jwtSeed: SecretKeySpec, hasher: PasswordHash)
  extends CreateUser.Service[Task] with Login.Service[Task] {
  def asUserApi: UserApi[Task] = UserApi[Task](this, this)

  private val registeredUsers = CrudServicesAnyCollectionMongo(database).asCrudServicesAnyCollection.forCollection[users.RegisteredUser]
  private val delegate = CreateUserForWriter[Task](registeredUsers.insert, jwtSeed, hasher)

  override def createUser(request: CreateUser.Request): Task[CreateUser.Response] = delegate.createUser(request)

  override def login(request: Login.Request): Task[Login.Response] = {
    val criteria = {
      val nameOk = Filters.or(
        Filters.eq("data.email", request.usernameOrEmail),
        Filters.eq("data.userName", request.usernameOrEmail)
      )
      Filters.and(nameOk, Filters.eq("data.password", hasher(request.password)))
    }

    database.latest[RegisteredUser].findFirst(criteria, true).map {
      case None => Login.Response.empty()
      case Some(userRecord) =>
        import RichClaims._
        val user = userRecord.data.asClaims
        Login.Response(user.asToken(jwtSeed), user)
    }
  }

  // TODO - remove the token
  override def logout(userToken: String): Task[Boolean] = Task.pure(true)
}
