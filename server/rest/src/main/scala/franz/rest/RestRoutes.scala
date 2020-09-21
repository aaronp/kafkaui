package franz.rest

import franz.DataNamespace
import cats.effect.{ConcurrentEffect, ContextShift, Sync}
import cats.implicits._
import cats.{NonEmptyParallel, Parallel}
import franz.UserApi
import franz.AppServices
import franz.data.crud.routes.{CrudRoutesAnyCollection, SearchRoutes}
import franz.data.diff.Diff
import franz.data.diff.routes.DiffRoutes
import franz.data.index.routes.{CompoundIndexRoutes, IndexRoutes}
import franz.data.query.rotues.MatchWeightsRoutes
import franz.rest.RestApp.authedRoutes
import franz.users.{AdminApi, PermissionPredicate, WebUser}
import org.http4s.AuthedRoutes

/**
 * A place to initialize the REST routes based on services created from a configuration
 */
object RestRoutes {

  def apply[F[_] : ConcurrentEffect : ContextShift : Parallel](services: RestServices[F]): AuthedRoutes[WebUser, F] = {
    makeUserRoutes(services.userServices, services.adminServices) <+> makeAppRoutes(services.appServices, services.permissions)
  }

  def makeAppRoutes[F[_] : Sync : NonEmptyParallel](services: AppServices[F], permissions: PermissionPredicate[F]): AuthedRoutes[WebUser, F] = {

    import services._
    val diffServices: Diff.Service[F] = Diff(crudServices.readService)

    val searchRoutes = SearchRoutes(searchServices, permissions)

    CrudRoutesAnyCollection(DataNamespace, crudServices, permissions).routes <+> // get data in/out of this system
      searchRoutes <+> // search
      MatchWeightsRoutes(weights, permissions).routes <+> // CRUD for /matchweights
      CompoundIndexRoutes(indicesCrud, permissions).routes <+> // CRUD for admins adding compound indices
      DiffRoutes(diffServices, permissions).routes <+> // GET /diff/... for diffing two records
      IndexRoutes(matchServices, permissions).routes // GET /index/<value> - read the indices for a value
  }

  def makeUserRoutes[F[_] : ConcurrentEffect : ContextShift](appServices: UserApi[F],
                                                             adminServices: AdminApi[F]): AuthedRoutes[(WebUser), F] = {
    authedRoutes(appServices, adminServices)
  }
}
