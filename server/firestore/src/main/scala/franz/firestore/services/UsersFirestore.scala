package franz.firestore.services

import cats.syntax.option._
import com.google.cloud.firestore.{DocumentSnapshot, Firestore, Query}
import com.typesafe.scalalogging.StrictLogging
import franz.UserApi
import franz.data.crud.{CrudServicesAnyCollection, InsertRecord}
import franz.data.{VersionedJson, VersionedJsonResponse, collectionNameFor}
import franz.firestore.{CrudServicesAnyCollectionFirestore, FS, FSEnv, FSRead}
import franz.jwt.PasswordHash
import franz.users._
import io.circe.DecodingFailure
import javax.crypto.spec.SecretKeySpec
import zio.{IO, RIO, ZIO}

object UsersFirestore {

  def findUser(fs: Firestore, usernameOrEmail: String): ZIO[Any, DecodingFailure, Option[RegisteredUser]] = {
    val eitherT: ZIO[Any, NoSuchElementException, DocumentSnapshot] = {
      val collName = collectionNameFor[RegisteredUser]
      val users = fs.collection(collName)
      val byEmail: Query = users.whereEqualTo("latest.data.email", usernameOrEmail)
      val byEmailTask: ZIO[Any, NoSuchElementException, DocumentSnapshot] = FSRead.execHead(byEmail)
      val byUserName = users.whereEqualTo("latest.data.userName", usernameOrEmail)
      val byUserNameTask: ZIO[Any, NoSuchElementException, DocumentSnapshot] = FSRead.execHead(byUserName)
      byEmailTask.disconnect.race(byUserNameTask.disconnect)
    }

    eitherT.either.flatMap {
      case Left(_) => IO.succeed(None)
      case Right(doc) =>
        for {
          versionedJson <- FSRead.parseAsVersionedJson(doc)
          user <- IO.fromEither(versionedJson.data.as[RegisteredUser])
        } yield user.some
    }
  }
}

case class UsersFirestore(jwtSeed: SecretKeySpec, hasher: PasswordHash)
  extends CreateUser.Service[FS]
    with Login.Service[FS]
    with UserApi[FS]
    with StrictLogging {

  private val delegate = {
    import zio.interop.catz._
//    import zio.interop.catz._
    val csacf: CrudServicesAnyCollection[FS] = CrudServicesAnyCollectionFirestore()
    val writer: InsertRecord.Service[FS, VersionedJson, VersionedJsonResponse] = csacf.forCollection[RegisteredUser].insert
    CreateUserForWriter(writer, jwtSeed, hasher)
  }

  override def createUser(newUser: CreateUser.Request): FS[CreateUser.Response] = {
    delegate.createUser(newUser)
  }

  override def login(request: Login.Request): FS[Login.Response] = {
    RIO.accessM[FSEnv] { env: FSEnv =>
      val fs: Firestore = env.get[Firestore]
      UsersFirestore.findUser(fs, request.usernameOrEmail).map {
        case None =>
          logger.info(s"User '${request.usernameOrEmail}' not found'")
          Login.Response(None)
        case Some(userRecord) =>
          val hashedPwd = hasher(request.password)
          if (hashedPwd == userRecord.password) {
            import RichClaims._
            val user = userRecord.asClaims
            Login.Response(user.asToken(jwtSeed), user)
          } else {
            logger.info(s"Invalid password for '${request.usernameOrEmail}'")
            Login.Response(None)
          }
      }
    }
  }

  override def logout(userToken: String): FS[Boolean] = {
    // TODO - update redis
    IO.succeed(true)
  }
}
