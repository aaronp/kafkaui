package franz.users

import cats.{ApplicativeError, Monad, ~>}
import cats.effect.Sync
import franz.data.VersionedRecord
import franz.rest.Swagger
import io.circe.Json

/**
 * Just a convenience to package in the various services under one API
 *
 * @tparam F the effect type
 */
trait AdminApi[F[_]]
  extends Login[F]
    with Roles[F]
    with UserRoles[F] { self =>
//  def mapK[G[_]](implicit ev: F ~> G) = {
//    new AdminApi[G] {
//      override def loginService: Login.Service[G] = {
//        val login = (r : Login.Request) => ev(self.loginService.login(r))
//        val logout = (token : String) => ev(self.loginService.logout(token))
//        Login.liftF[G](login, logout)
//      }
//
//      override def userRoles: UserRoles.Service[G] = new UserRoles.Service[G] {
//        override def associateUser(request: VersionedRecord[UserRoles.AssociateRolesWithUser]): G[UserRoles.SetUserRolesResponse] = {
//          ev(self.userRoles.associateUser(request))
//        }
//        override def roleRecordsForUser(userId: String): G[Option[VersionedRecord[Set[String]]]] = {
//          ev(self.userRoles.roleRecordsForUser(userId))
//        }
//      }
//
//      override def rolesService: Roles.Service[G] = {
//        self.rolesService.mapK[G]
//        ???
//      }
//    }
//  }
}

object AdminApi {
  def apply[F[_]](loginInstance: Login.Service[F],
                   rolesInstance: Roles.Service[F],
                   userRolesInstance: UserRoles.Service[F]): AdminApi[F] = {
    new AdminApi[F] {
      override val loginService = loginInstance

      override val rolesService = rolesInstance

      override val userRoles = userRolesInstance
    }
  }

  /**
   * Return a [[AdminApi]] instance using the given json client
   *
   * @param client      the client which knows how to send/receive JSON requests
   * @param userSwagger the user request definitions
   * @tparam F the effect type
   * @return a GleichApi instance
   */
  def client[F[_]](client: Swagger.Client[F, Json],
                           userSwagger: UserSwagger = UserSwagger.Rest
                          )(implicit monad : Monad[F], appErr : ApplicativeError[F, Throwable]): AdminApi[F] = {

    AdminApi[F](
      Login.Client(client, Swagger.parserForJson[F, Login.Response], Swagger.parserForJson[F, Boolean]),
      Roles.Client(client, userSwagger),
      UserRoles.Client(client, userSwagger)
    )
  }
}
