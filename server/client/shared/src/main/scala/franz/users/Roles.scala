package franz.users

import cats.effect.{IO, Sync}
import cats.{ApplicativeError, Functor, Monad}
import franz.data._
import franz.data.crud._
import franz.rest.Swagger
import io.circe.Json

import scala.util.Properties

trait Roles[F[_]] {
  def rolesService: Roles.Service[F]
}

object Roles {

  type UpdateResponse = VersionedResponse[RolePermissions]

  trait Service[F[_]]
    extends Roles[F]
      with InsertRecord.Service[F, VersionedRecord[RolePermissions], UpdateResponse]
      with DeleteRecord.Service[F, String, Option[VersionedRecord[RolePermissions]]]
      with ListRecords.Service[F, List[VersionedRecord[RolePermissions]]]
      with PermissionPredicate[F] {
    self =>

    override def rolesService: Service[F] = self

    def mkRoles(roleName: String, permissions: Set[String], userName: String = Properties.userName): VersionedRecord[RolePermissions] = {
      import VersionedRecord.syntax._
      RolePermissions(roleName, permissions).versionedRecord(userName)
    }

    final def updateRole(role: VersionedRecord[RolePermissions]): F[UpdateResponse] = {
      insertService.insert(role)
    }

    /**
     * @param roleName the role to remove
     * @return true if the role was removed
     */
    def removeRole(roleName: String) = {
      deleteService.delete(roleName)
    }

    def roles(range: QueryRange = QueryRange.Default) = listService.list(range)

    /** @return a map of all permissions which are associated with a given role (e.g. permissions keyed by role)
     */
    def permissionsByRole(): F[Map[String, Set[String]]]


    /** @return a map of all roles which are associated with a given permission (e.g. roles keyed by permissions)
     */
    def rolesByPermissions(): F[Map[String, Set[String]]]

    override final def isPermitted(userRoles: Set[String], permission: String)(implicit fnc: Functor[F]): F[Boolean] = {
      fnc.map(roles()) { set =>
        set.exists {
          case VersionedRecord(RolePermissions(name, perms), _, _, _, _) =>
            // user has this role and the role contains the permission(s)
            userRoles.contains(name) && perms.contains(permission)
        }
      }
    }
  }

  def inMemory: Roles.Service[IO] = {
    val cache: VersionedCache[IO, String, VersionedRecord[RolePermissions]] = VersionedCache.unsafe[IO, String, VersionedRecord[RolePermissions]]
    apply[IO](cache)
  }

  def empty[F[_] : Sync]: F[Instance[F]] = {
    import cats.implicits._
    VersionedCache.empty[F, String, VersionedRecord[RolePermissions]].map {
      cache: VersionedCache[F, String, VersionedRecord[RolePermissions]] =>
        apply[F](cache)
    }
  }

  def apply[F[_] : Functor](base: InsertRecord[F, VersionedRecord[RolePermissions], UpdateResponse]
    with DeleteRecord[F, String, Option[VersionedRecord[RolePermissions]]]
    with ListRecords[F, List[VersionedRecord[RolePermissions]]]): Instance[F] = {
    val delegate = new InsertRecord.Service[F, VersionedRecord[RolePermissions], UpdateResponse]
      with DeleteRecord.Service[F, String, Option[VersionedRecord[RolePermissions]]]
      with ListRecords.Service[F, List[VersionedRecord[RolePermissions]]] {

      override def insert(request: VersionedRecord[RolePermissions]) = {
        base.insertService.insert(request)
      }

      override def delete(record: String): F[Option[VersionedRecord[RolePermissions]]] = base.deleteService.delete(record)

      override def list(range: QueryRange): F[List[VersionedRecord[RolePermissions]]] = base.listService.list(range)
    }
    new Instance(delegate)
  }


  class Instance[F[_] : Functor](delegate: InsertRecord.Service[F, VersionedRecord[RolePermissions], UpdateResponse]
    with DeleteRecord.Service[F, String, Option[VersionedRecord[RolePermissions]]]
    with ListRecords.Service[F, List[VersionedRecord[RolePermissions]]]) extends Service[F] {
    override def deleteService = delegate.deleteService

    override def insertService = delegate.insertService

    override def listService = delegate.listService

    override def insert(request: VersionedRecord[RolePermissions]): F[UpdateResponse] = delegate.insert(request)

    override def delete(record: String) = delegate.delete(record)

    override def list(range: QueryRange): F[List[VersionedRecord[RolePermissions]]] = delegate.list(range)

    /** @return a map of all permissions which are associated with a given role (e.g. permissions keyed by role)
     */
    override def permissionsByRole(): F[Map[String, Set[String]]] = {
      Functor[F].map(roles()) { list =>
        list.foldLeft(Map.empty[String, Set[String]]) {
          case (map, VersionedRecord(RolePermissions(role, perms), _, _, _, _)) =>
            val permSet = map.getOrElse(role, Set.empty[String]) ++ perms
            map.updated(role, permSet)
        }
      }
    }

    /** @return a map of all roles which are associated with a given permission (e.g. roles keyed by permissions)
     */
    override def rolesByPermissions(): F[Map[String, Set[String]]] = Functor[F].map(permissionsByRole())(swap)
  }

  /**
   * Represents the mapping between a role and a permission
   *
   * @param roleName
   * @param permissions
   */
  final case class RolePermissions(roleName: String, permissions: Set[String])

  object RolePermissions {
    implicit val encoder = io.circe.generic.semiauto.deriveEncoder[RolePermissions]
    implicit val decoder = io.circe.generic.semiauto.deriveDecoder[RolePermissions]
  }


  final case class Client[F[_], A](client: Swagger.Client[F, Json],
                                   swagger: UserSwagger = UserSwagger.Rest)(implicit monad: Monad[F], appErr: ApplicativeError[F, Throwable])
    extends Service[F] {

    import cats.syntax.flatMap._

    override def permissionsByRole(): F[Map[String, Set[String]]] = {
      monad.map(roles()) { list =>
        list.foldLeft(Map.empty[String, Set[String]]) {
          case (map, VersionedRecord(RolePermissions(role, perms), _, _, _, _)) =>
            val permSet = map.getOrElse(role, Set.empty[String]) ++ perms
            map.updated(role, permSet)
        }
      }
    }

    override def rolesByPermissions(): F[Map[String, Set[String]]] = monad.map(permissionsByRole())(swap)

    override def insert(request: VersionedRecord[RolePermissions]): F[UpdateResponse] = {
      val parser = Swagger.parserForJson[F, UpdateResponse]
      client.run(swagger.roles.createRequest(request)).flatMap(parser.apply)
    }

    override def delete(record: String) = {
      val parser = Swagger.parserForJson[F, Option[VersionedRecord[RolePermissions]]]
      client.run(swagger.roles.removeRole(record)).flatMap(parser.apply)
    }

    override def list(range: QueryRange): F[List[VersionedRecord[RolePermissions]]] = {
      val parser = Swagger.parserForJson[F, List[VersionedRecord[RolePermissions]]]
      client.run(swagger.roles.listRolesRequest(range)).flatMap(parser.apply)
    }
  }

}
