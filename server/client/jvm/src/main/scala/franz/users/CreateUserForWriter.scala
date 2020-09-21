package franz.users

import cats.Functor
import cats.syntax.functor._
import franz.data.VersionedRecord.syntax._
import franz.data.crud.InsertRecord
import franz.data.{VersionedJson, VersionedJsonResponse}
import franz.jwt.{Hmac256, PasswordHash}
import io.circe.syntax._
import javax.crypto.spec.SecretKeySpec

import scala.util.Properties

case class CreateUserForWriter[F[_] : Functor](write: InsertRecord.Service[F, VersionedJson, VersionedJsonResponse], jwtSeed: SecretKeySpec, hasher: PasswordHash) extends CreateUser.Service[F] {
  override def createUser(request: CreateUser.Request): F[CreateUser.Response] = {
    val newUser = RegisteredUser(request.copy(password = hasher(request.password)))

    // usually the record's user id is from the logged-in user. But in the case of users themselves that would just be the same as their own names,
    // so we use this process's service user
    val userJson = {
      // we also want consistent user ids for testing, though this might be a stupid reason
      val userId = Hmac256.encodeToString(jwtSeed, newUser.userName + newUser.email)
      newUser.asJson.versionedRecord(id = userId, userId = Properties.userName)
    }
    write.insert(userJson).map { writeResponse: VersionedJsonResponse =>
      if (writeResponse.isSuccess) {
        CreateUser.Response.CreatedUser(newUser.asClaims)
      } else {
        CreateUser.Response.InvalidRequest("User already exists")
      }
    }
  }
}
