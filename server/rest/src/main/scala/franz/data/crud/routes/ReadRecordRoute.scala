package franz.data.crud.routes

import cats.data.Validated.{Invalid, Valid}
import cats.effect.Sync
import franz.data._
import franz.data.crud.{CrudServicesAnyCollection, ReadRecord}
import franz.users.routes._
import franz.users.{PermissionPredicate, User, WebUser}
import io.circe.Encoder
import org.http4s.dsl.Http4sDsl
import org.http4s.{AuthedRequest, AuthedRoutes, Response}

/**
 * Routes to support services which can read either versioned or latest records
 */
object ReadRecordRoute {

  def read[F[_] : Sync, A: Encoder](authRequest: AuthedRequest[F, WebUser],
                                    collectionName: CollectionName,
                                    id: Id,
                                    service: ReadRecord.Service[F, (User, RecordCoords), Option[VersionedRecord[A]]],
                                    permissions: PermissionPredicate[F],
                                    dsl: Http4sDsl[F] = Http4sDsl[F]): F[Response[F]] = {
    import cats.syntax.flatMap._
    val (jwt, user) = authRequest.context
    val queryParams: Map[String, Seq[String]] = authRequest.req.multiParams

    val requiredPermission = asPermission(authRequest)
    permissions.isPermitted(user, requiredPermission).flatMap {
      case true =>
        RecordVersion.parseFromQueryParams(CrudServicesAnyCollection.VersionQueryParam, queryParams) match {
          case Valid(version) =>
            service.read(user -> RecordCoords(collectionName, id, version)).flatMap(ok(_, jwt, dsl))
          case Invalid(err) =>
            val nope = badRequest[F](s"Invalid '${CrudServicesAnyCollection.VersionQueryParam}' query parameter: ${err}")
            setToken(nope, jwt)
        }
      case false =>
        setToken(notAuthorised(dsl.Unauthorized, requiredPermission), jwt)

    }
  }


  def data[F[_] : Sync](namespace: String,
                        service: ReadRecord.Service[F, (User, RecordCoords), Option[VersionedJson]],
                        permissions: PermissionPredicate[F],
                        dsl: Http4sDsl[F] = Http4sDsl[F]): AuthedRoutes[WebUser, F] = {
    import dsl._

    val Namespace = namespace
    AuthedRoutes.of {
      case authRequest@GET -> Root / Namespace / collectionName / id as _ =>
        read(authRequest, collectionName, id, service, permissions)
    }
  }

  def dataNoUser[F[_] : Sync](namespace: String,
                              readLatestService: ReadRecord.Service[F, RecordCoords, Option[VersionedJson]],
                              permissions: PermissionPredicate[F],
                              dsl: Http4sDsl[F] = Http4sDsl[F]): AuthedRoutes[WebUser, F] = {
    val ignoreUser = ReadRecord.contraMap[F, (User, RecordCoords), RecordCoords, Option[VersionedJson]](readLatestService) {
      case (_, coords) => coords
    }
    data(namespace, ignoreUser, permissions)
  }
}
