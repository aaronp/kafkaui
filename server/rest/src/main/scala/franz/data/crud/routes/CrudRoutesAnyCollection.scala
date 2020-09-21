package franz.data.crud.routes

import cats.effect.Sync
import cats.implicits._
import franz.data.crud.CrudServicesAnyCollection
import franz.users.{PermissionPredicate, WebUser}
import org.http4s.AuthedRoutes

case class CrudRoutesAnyCollection[F[_] : Sync](namespace : String, app: CrudServicesAnyCollection[F], permissions: PermissionPredicate[F]) {

  def routes: AuthedRoutes[WebUser, F] = {
    insertRoute <+> readRoute <+> listRoute <+> deleteRoute
  }

  /** POST /data/<collection>
   *
   * @return the insert response
   */
  def insertRoute: AuthedRoutes[WebUser, F] = InsertRecordRoute.anyCollectionNoUser(namespace, app.insertService, permissions)

  /**
   * GET /data/<collection>/<id>
   * or
   * GET /data/<collection>/<id>?version=123
   *
   * @return the latest value for the given collection / id
   */
  def readRoute: AuthedRoutes[WebUser, F] = ReadRecordRoute.dataNoUser(namespace, app.readService, permissions)

  def listRoute: AuthedRoutes[WebUser, F] = ListRecordsRoute.single(namespace, app.listService, permissions)

  def deleteRoute: AuthedRoutes[WebUser, F] = DeleteRecordRoute.singleNoUser(namespace, app.deleteService, permissions)
}

