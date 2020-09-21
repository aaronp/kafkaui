package franz.data.crud.routes

import cats.effect.Sync
import cats.implicits._
import franz.data.crud.{CrudServices, DeleteRecord, InsertRecord, ReadRecord}
import franz.data.{CollectionName, Id, RecordCoords, VersionedRecord}
import franz.users.routes.{asPermission, notAuthorised, ok, setToken}
import franz.users.{PermissionPredicate, User, WebUser}
import io.circe.{Decoder, Encoder}
import org.http4s.AuthedRoutes
import org.http4s.dsl.Http4sDsl

case class CrudRoutes[F[_] : Sync, A: Encoder : Decoder](namespace: CollectionName, services: CrudServices[F, A], permissions: PermissionPredicate[F]) {

  def routes: AuthedRoutes[WebUser, F] = {
    insertRoute <+> readRoute <+> listRoute <+> deleteRoute
  }

  def insertRoute: AuthedRoutes[WebUser, F] = InsertRecordRoute.single(namespace, InsertRecord.ignoringUser(services.insertService), permissions)

  def readRoute: AuthedRoutes[WebUser, F] = CrudRoutes.ReadRoute(namespace, ReadRecord.ignoringUser(services.readService), permissions)

  def listRoute: AuthedRoutes[WebUser, F] = ListRecordsRoute.single(namespace, services.listService, permissions)

  def deleteRoute: AuthedRoutes[WebUser, F] = CrudRoutes.delete(namespace, DeleteRecord.ignoringUser(services.deleteService), permissions)
}

object CrudRoutes {

  object ReadRoute {
    def apply[F[_] : Sync, A: Encoder](collection: CollectionName,
                                       service: ReadRecord.Service[F, (User, RecordCoords), Option[VersionedRecord[A]]],
                                       permissions: PermissionPredicate[F],
                                       dsl: Http4sDsl[F] = Http4sDsl[F]): AuthedRoutes[WebUser, F] = {
      import dsl._
      val Namespace = collection
      AuthedRoutes.of {
        case authRequest@GET -> Root / Namespace / id as _ =>
          ReadRecordRoute.read(authRequest, collection, id, service, permissions, dsl)
      }
    }
  }

  def delete[F[_] : Sync, R: Encoder](collectionName: CollectionName,
                                      service: DeleteRecord.Service[F, (User, Id), R],
                                      permissions: PermissionPredicate[F], dsl: Http4sDsl[F] = Http4sDsl[F]): AuthedRoutes[WebUser, F] = {


    import dsl._

    val Namespace = collectionName

    AuthedRoutes.of {
      case authRequest@DELETE -> Root / Namespace / id as jwtUser =>
        val (jwt, user) = jwtUser
        val requiredPermission: String = asPermission(authRequest)
        permissions.isPermitted(user, requiredPermission).flatMap {
          case true =>
            for {
              response <- service.delete(user -> id)
              resp <- ok(response, jwt, dsl)
            } yield resp
          case false => setToken(notAuthorised(dsl.Unauthorized, requiredPermission), jwt)
        }
    }
  }
}
