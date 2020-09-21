package franz.data.query.rotues

import cats.effect.Sync
import franz.data.crud.{CrudServices, CrudServicesInMemory}
import franz.data.crud.routes.CrudRoutes
import franz.data.index.MatchWeights
import franz.users.PermissionPredicate

object MatchWeightsRoutes {

  def apply[F[_] : Sync](perms: PermissionPredicate[F]): CrudRoutes[F, MatchWeights] = {
    apply(CrudServicesInMemory[F, MatchWeights](true).services, perms)
  }

  def apply[F[_] : Sync](services: CrudServices[F, MatchWeights], perms: PermissionPredicate[F]): CrudRoutes[F, MatchWeights] = {
    CrudRoutes(MatchWeights.Namespace, services, perms)
  }
}
