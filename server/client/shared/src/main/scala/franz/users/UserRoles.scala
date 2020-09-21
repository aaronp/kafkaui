package franz.users

import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.{ApplicativeError, Functor, Monad}
import franz.data.VersionedRecord
import franz.rest.Swagger
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}

/**
 * A service for linking users with roles
 *
 * @tparam F
 */
trait UserRoles[F[_]] {
  def userRoles: UserRoles.Service[F]
}

object UserRoles {

  /**
   * The User roles service functions
   *
   * @tparam F the effect type
   */
  trait Service[F[_]] extends UserRoles[F] {
    self =>

    override def userRoles: UserRoles.Service[F] = self

    def associateUser(request: VersionedRecord[AssociateRolesWithUser]): F[SetUserRolesResponse]

    final def associateUser(userId: String, roleNames: Set[String], version: Int = 0): F[SetUserRolesResponse] = {
      require(userId != null, "null user id")
      import VersionedRecord.syntax._
      associateUser(AssociateRolesWithUser(userId, roleNames).versionedRecord(userId, version = version))
    }

    def roleRecordsForUser(userId: String): F[Option[VersionedRecord[Set[String]]]]

    def rolesForUser(userId: String)(implicit fctr: Functor[F]): F[Set[String]] = {
      import cats.syntax.functor._
      roleRecordsForUser(userId).map(_.map(_.data).getOrElse(Set.empty))
    }
  }

  final case class Client[F[_] : Monad, A](client: Swagger.Client[F, A],
                                           setUserRolesParser: Swagger.Parser[F, A, SetUserRolesResponse],
                                           roleRecordsForUserParser: Swagger.Parser[F, A, Option[VersionedRecord[Set[String]]]],
                                           swagger: UserSwagger) extends Service[F] {

    import cats.syntax.flatMap._

    override def associateUser(request: VersionedRecord[AssociateRolesWithUser]): F[SetUserRolesResponse] = {
      client.run(swagger.userRoles.associateRoles(request)).flatMap(setUserRolesParser.apply)
    }

    override def roleRecordsForUser(userId: String): F[Option[VersionedRecord[Set[String]]]] = {
      client.run(swagger.userRoles.listRoles(userId)).flatMap(roleRecordsForUserParser.apply)
    }
  }

  object Client {
    def apply[F[_]](client: Swagger.Client[F, Json],
                           swagger: UserSwagger = UserSwagger.Rest)(implicit monad : Monad[F], appErr : ApplicativeError[F, Throwable]): Client[F, Json] = {
      val p1 = Swagger.parserForJson[F, SetUserRolesResponse]
      val p2 = Swagger.parserForJson[F, Option[VersionedRecord[Set[String]]]]
      new Client[F, Json](client, p1, p2, swagger)
    }
  }

  def empty[F[_] : Functor : Sync]: F[Service[F]] = {
    import cats.syntax.functor._
    val fRef = Ref.of[F, Map[String, VersionedRecord[Set[String]]]](Map.empty[String, VersionedRecord[Set[String]]])
    fRef.map(x => apply(x))
  }

  def apply[F[_] : Functor](tokens: Ref[F, Map[String, VersionedRecord[Set[String]]]]): Service[F] = new InMemory[F](tokens)

  case class AssociateRolesWithUser(userId: String, roleNames: Set[String])

  object AssociateRolesWithUser {
    implicit val encoder = io.circe.generic.semiauto.deriveEncoder[AssociateRolesWithUser]
    implicit val decoder = io.circe.generic.semiauto.deriveDecoder[AssociateRolesWithUser]
  }

  type LatestRolesVersion = Int

  case class SetUserRolesResponse(updated: Either[String, LatestRolesVersion]) {
    def isSuccess = updated.isRight
  }

  object SetUserRolesResponse {
    def apply(err: String) = new SetUserRolesResponse(Left(err))

    def apply(LatestRolesVersion: LatestRolesVersion) = new SetUserRolesResponse(Right(LatestRolesVersion))

    implicit object Format extends Encoder[SetUserRolesResponse] {
      override def apply(a: SetUserRolesResponse): Json = {
        a.updated match {
          case Left(value) => value.asJson
          case Right(value) => value.asJson
        }
      }
    }

    implicit val decoder: Decoder[SetUserRolesResponse] = {
      val left = Decoder[String].map(x => SetUserRolesResponse(x))
      val right = Decoder[Int].map(x => SetUserRolesResponse(x))
      (left or right)
    }
  }

  case class InMemory[F[_] : Functor](rolesByUserId: Ref[F, Map[String, VersionedRecord[Set[String]]]]) extends Service[F] {

    import cats.syntax.functor._

    override def associateUser(request: VersionedRecord[AssociateRolesWithUser]) = {
      val AssociateRolesWithUser(userId, roleNames) = request.data
      val result: F[Option[SetUserRolesResponse]] = rolesByUserId.tryModify { map =>
        def update() = {
          map.updated(userId, request.map(_.roleNames)) -> SetUserRolesResponse(Right(request.version))
        }

        map.get(userId) match {
          case None => update()
          case Some(old) if old.version <= request.version => update()
          case Some(old) => map -> SetUserRolesResponse(Left(s"out-of-date version: ${old.version} is more recent than ${request.version}"))
        }
      }

      result.map {
        case None => SetUserRolesResponse(Left("Unable to modify roles"))
        case Some(response) => response
      }
    }

    override def roleRecordsForUser(userId: String): F[Option[VersionedRecord[Set[String]]]] = {
      rolesByUserId.get.map(_.get(userId))
    }
  }
}
