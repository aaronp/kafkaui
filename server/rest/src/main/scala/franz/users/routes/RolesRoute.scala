package franz.users.routes

import cats.effect.Sync
import cats.implicits._
import franz.data.crud.routes.{InsertRecordRoute, ListRecordsRoute}
import franz.data.crud.{DeleteRecord, InsertRecord}
import franz.data.{Id, VersionedJson, VersionedRecord}
import franz.users.Roles.{RolePermissions, UpdateResponse}
import franz.users.{PermissionPredicate, Roles, UserSwagger, WebUser}
import org.http4s.AuthedRoutes
import org.http4s.dsl.Http4sDsl

object RolesRoute {
  private def path = Seq(UserSwagger.RBAC, UserSwagger.Roles)

  def apply[F[_] : Sync](service: Roles.Service[F]): AuthedRoutes[WebUser, F] = {
    val listRoute = ListRecordsRoute(path, service.listService, service)

    val insertRoute: AuthedRoutes[WebUser, F] = {
      val insertService  = InsertRecord.ignoringUser(service.insertService).insertService
      InsertRecordRoute[F, VersionedRecord[Roles.RolePermissions], UpdateResponse](path, insertService, service)
    }

    val deleteRoute = deleteRole(service.deleteService, service)

    listRoute <+> insertRoute <+> deleteRoute
  }

  def deleteRole[F[_] : Sync](service: DeleteRecord.Service[F, Id, Option[VersionedRecord[RolePermissions]]],
                              permissions: PermissionPredicate[F], dsl: Http4sDsl[F] = Http4sDsl[F]): AuthedRoutes[WebUser, F] = {
    import dsl._
    AuthedRoutes.of {
      case authRequest@DELETE -> Root / UserSwagger.RBAC / UserSwagger.Roles / id as jwtUser =>
        val (jwt, user) = jwtUser
        val requiredPermission: String = {
          val pathOhneId = popPath(authRequest.req.uri)
          asPermission(authRequest.req.method, pathOhneId)
        }
        permissions.isPermitted(user, requiredPermission).flatMap {
          case true => service.delete(id).flatMap(ok(_, jwt, dsl))
          case false => setToken(notAuthorised(dsl.Unauthorized, requiredPermission), jwt)
        }
    }
  }
}
