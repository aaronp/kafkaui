package franz.data.index.routes

import cats.effect.Sync
import franz.data.crud.CrudServices
import franz.data.crud.routes.CrudRoutes
import franz.data.index.CompoundIndex
import franz.users.PermissionPredicate

/**
 * Means to create compound indices to be used when saving documents
 */
object CompoundIndexRoutes {
  def apply[F[_] : Sync](perms: PermissionPredicate[F]): CrudRoutes[F, Seq[CompoundIndex]] = {
    val svc: CrudServices[F, Seq[CompoundIndex]] = CompoundIndex.inMemory
    apply(svc, perms)
  }

  /**
   * The ID for the Compound Indices would be the collection against which they are saved
   * @param services
   * @param perms
   * @tparam F
   * @return
   */
  def apply[F[_] : Sync](services: CrudServices[F, Seq[CompoundIndex]], perms: PermissionPredicate[F]): CrudRoutes[F, Seq[CompoundIndex]] = {
    CrudRoutes(CompoundIndex.Namespace, services, perms)
  }
}
