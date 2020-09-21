package franz.users.routes

import cats.effect.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._
import franz.data.{Versioned, VersionedRecord}
import franz.rest.parseBodyAs
import franz.users.{Roles, UserRoles}
import org.http4s.circe._
import org.http4s.{Request, Response}

class UserRolesHttpClient[F[_] : Sync](handler: Request[F] => F[Response[F]], restPrefix: String = "/rest") extends UserRoles.Service[F] {

  import franz.rest.Swagger4s.implicits._
  import org.http4s.circe._

  private val Requests = franz.users.UserSwagger(restPrefix).userRoles

  override def associateUser(request: VersionedRecord[UserRoles.AssociateRolesWithUser]): F[UserRoles.SetUserRolesResponse] = {
    implicit val decoder = jsonOf[F, Roles.UpdateResponse]
    val httpRequest = Requests.associateRoles(request)
    for {
      resp <- handler(httpRequest.toHttp4s[F])
      body <- parseBodyAs[F, UserRoles.SetUserRolesResponse](resp)
    } yield body
  }

  override def roleRecordsForUser(userId: String) = {
    implicit val decoder = jsonOf[F, Roles.UpdateResponse]
    val httpRequest = Requests.listRoles(userId)
    for {
      resp <- handler(httpRequest.toHttp4s[F])
      body <- parseBodyAs[F, Option[VersionedRecord[Set[String]]]](resp)
    } yield body
  }
}
