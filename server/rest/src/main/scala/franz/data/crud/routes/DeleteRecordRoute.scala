package franz.data.crud.routes

import cats.effect.Sync
import cats.implicits._
import franz.data.crud.DeleteRecord
import franz.data.{CollectionName, Id}
import franz.users.routes._
import franz.users.{PermissionPredicate, User, WebUser}
import io.circe.Encoder
import org.http4s.AuthedRoutes
import org.http4s.dsl.Http4sDsl


object DeleteRecordRoute {

  def singleNoUser[F[_] : Sync, R : Encoder](singlePath: String,
                                service: DeleteRecord.Service[F, (CollectionName, Id), R],
                                permissions: PermissionPredicate[F]): AuthedRoutes[WebUser, F] = {
    val ignoredUser = DeleteRecord.contraMap[F, (CollectionName, Id), (User, CollectionName, Id), R](service) {
      case (_, collection, id) => (collection, id)
    }
    single(singlePath, ignoredUser, permissions)
  }

  def single[F[_] : Sync, R : Encoder](singlePath: String,
                          service: DeleteRecord.Service[F, (User, CollectionName, Id), R],
                          permissions: PermissionPredicate[F]): AuthedRoutes[WebUser, F] = {
    apply(Seq(singlePath), service, permissions, Http4sDsl[F])
  }

  def apply[F[_] : Sync, R : Encoder](path: Seq[String],
                                      service: DeleteRecord.Service[F, (User, CollectionName, Id), R],
                                      permissions: PermissionPredicate[F], dsl: Http4sDsl[F] = Http4sDsl[F]): AuthedRoutes[WebUser, F] = {


    import dsl._

    val ThisPath = join(path, dsl)

    AuthedRoutes.of {
      case authRequest@DELETE -> ThisPath / collectionName / id as jwtUser =>
        val (jwt, user) = jwtUser
        val requiredPermission: String = asPermission(authRequest)
        permissions.isPermitted(user, requiredPermission).flatMap {
          case true =>
            for {
              response <- service.delete((user, collectionName, id))
              resp <- ok(response, jwt, dsl)
            } yield resp
          case false => setToken(notAuthorised(dsl.Unauthorized, requiredPermission), jwt)
        }
    }
  }
}
