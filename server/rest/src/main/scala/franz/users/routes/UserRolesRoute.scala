package franz.users
package routes

import cats.effect.{IO, Sync}
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.{Applicative, Monad}
import franz.data.VersionedRecord
import franz.users.UserRoles.{AssociateRolesWithUser, SetUserRolesResponse}
import franz.users.UserSwagger._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.{AuthedRoutes, Request, Status}


object UserRolesRoute {

  def apply[F[_] : Sync](roles: PermissionPredicate[F] with UserRoles[F]): AuthedRoutes[WebUser, F] = {
    apply(roles, roles)
  }

  def apply[F[_] : Sync](roles: PermissionPredicate[F], service: UserRoles[F]): AuthedRoutes[WebUser, F] = {
    val dsl: Http4sDsl[F] = new Http4sDsl[F] {}
    makeRoutes(roles, service, dsl)
  }

  def forIO(roles: PermissionPredicate[IO], service: UserRoles[IO]): AuthedRoutes[WebUser, IO] = {
    val dsl = org.http4s.dsl.io
    makeRoutes(roles, service, dsl)
  }


  private def makeRoutes[F[_] : Sync : Monad](roles: PermissionPredicate[F], service: UserRoles[F], dsl: Http4sDsl[F]): AuthedRoutes[WebUser, F] = {
    import dsl._
    AuthedRoutes.of {
      case authRequest@GET -> Root / RBAC / UserRole / userId as jwtUser =>
        val (jwt, callingUser) = jwtUser

        // TODO - revisit permissioning model
        val requiredPermission: String = Permissions.userRoles.canList(userId)
        listRoles(roles, service, requiredPermission, authRequest.req, userId, callingUser).flatMap {
          case Right(response) => ok(response, jwt, dsl)
          case Left(missingPermission) => setToken(notAuthorised(dsl.Unauthorized, missingPermission), jwt)
        }
      case authRequest@POST -> Root / RBAC / UserRole as jwtUser =>
        val (jwt, callingUser) = jwtUser

        associateUser(roles, service, authRequest.req, callingUser).flatMap {
          case Right(response) if response.isSuccess => ok(response, jwt, dsl)
          case Right(response) => ok(response, jwt, dsl).map(_.withStatus(Status.BadRequest))
          case Left(missingPermission) => setToken(notAuthorised(dsl.Unauthorized, missingPermission), jwt)
        }
    }
  }

  private def associateUser[F[_] : Sync](roles: PermissionPredicate[F], service: UserRoles[F], req: Request[F], user: User): F[Either[String, SetUserRolesResponse]] = {
    implicit val decoder = jsonOf[F, VersionedRecord[AssociateRolesWithUser]]

    req.as[VersionedRecord[AssociateRolesWithUser]].flatMap { setRoles =>
      val requiredPermission: String = Permissions.userRoles.canAssign(setRoles.data.roleNames)

      roles.isPermitted(user, requiredPermission).flatMap {
        case true => service.userRoles.associateUser(setRoles).map(_.asRight[String])
        case false => Applicative[F].pure(Left(requiredPermission))
      }
    }
  }

  private def listRoles[F[_] : Sync](roles: PermissionPredicate[F], service: UserRoles[F], routePermission: String, req: Request[F], userId: String, user: User): F[Either[String, Option[VersionedRecord[Set[String]]]]] = {
    roles.isPermitted(user, routePermission).flatMap {
      case true =>
        val found: F[Option[VersionedRecord[Set[String]]]] = service.userRoles.roleRecordsForUser(userId)
        found.map(_.asRight[String])
      case false => Applicative[F].pure(Left(routePermission))
    }
  }
}
