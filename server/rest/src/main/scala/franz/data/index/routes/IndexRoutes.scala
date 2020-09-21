package franz.data.index.routes

import cats.data.Validated.{Invalid, Valid}
import cats.effect.Sync
import cats.implicits._
import franz.data.crud.{CrudServicesAnyCollection, ReadRecord}
import franz.data.index.{IndexedValue, AssociationQueries, RecordAssociations}
import franz.data.{IndexValue, RecordCoords, RecordVersion}
import franz.users.routes._
import franz.users.{PermissionPredicate, WebUser}
import io.circe.Json
import org.http4s.AuthedRoutes
import org.http4s.circe.jsonOf
import org.http4s.dsl.Http4sDsl

/**
 *
 * {{{
 *   GET /index/read/<name>  # read an index
 *   GET /index/match/<collection>/<id>?version=xyz  # return matches for a particular record
 * }}}
 *
 * @param services
 * @param permissions
 * @tparam F
 */
case class IndexRoutes[F[_] : Sync](services: AssociationQueries[F], permissions: PermissionPredicate[F]) {

  def routes: AuthedRoutes[WebUser, F] = {
    readIndexRoute <+> matchRecordRoute <+> matchEntityRoute
  }

  def readIndexRoute: AuthedRoutes[WebUser, F] = IndexRoutes.ReadIndexRoute[F](services.readIndex, permissions)

  def matchEntityRoute: AuthedRoutes[WebUser, F] = IndexRoutes.MatchEntityRoute[F](services.matchEntity, permissions)

  def matchRecordRoute: AuthedRoutes[WebUser, F] = IndexRoutes.MatchRecordRoute[F](services.matchRecord, permissions)
}

object IndexRoutes {

  object ReadIndexRoute {
    def apply[F[_] : Sync](service: ReadRecord.Service[F, IndexValue, Option[IndexedValue]],
                           permissions: PermissionPredicate[F], dsl: Http4sDsl[F] = Http4sDsl[F]): AuthedRoutes[WebUser, F] = {
      import dsl._

      AuthedRoutes.of {
        case authRequest@GET -> Root / AssociationQueries.Namespace / "index" / indexValue as jwtUser =>
          val requiredPermission: String = asPermission(authRequest)
          val (jwt, user) = jwtUser

          permissions.isPermitted(user, requiredPermission).flatMap {
            case true => service.read(indexValue).flatMap(ok(_, jwt, dsl))
            case false => setToken(notAuthorised(dsl.Unauthorized, requiredPermission), jwt)
          }
      }
    }
  }

  object MatchRecordRoute {
    def apply[F[_] : Sync](matchService: ReadRecord.Service[F, Json, RecordAssociations],
                           permissions: PermissionPredicate[F], dsl: Http4sDsl[F] = Http4sDsl[F]): AuthedRoutes[WebUser, F] = {
      import dsl._

      AuthedRoutes.of {
        case authRequest@POST -> Root / AssociationQueries.Namespace as jwtUser =>
          val requiredPermission: String = asPermission(authRequest)
          val (jwt, user) = jwtUser
          implicit val decoder = jsonOf[F, Json]
          permissions.isPermitted(user, requiredPermission).flatMap {
            case true =>
              for {
                body <- authRequest.req.as[Json]
                response <- matchService.read(body)
                resp <- ok(response, jwt, dsl)
              } yield resp
            case false => setToken(notAuthorised(dsl.Unauthorized, requiredPermission), jwt)
          }
      }
    }
  }

  object MatchEntityRoute {
    def apply[F[_] : Sync](matchEntity: ReadRecord.Service[F, RecordCoords, RecordAssociations],
                           permissions: PermissionPredicate[F], dsl: Http4sDsl[F] = Http4sDsl[F]): AuthedRoutes[WebUser, F] = {
      import dsl._

      AuthedRoutes.of {
        case authRequest@GET -> Root / AssociationQueries.Namespace / "match" / collectionName / id as jwtUser =>
          val requiredPermission: String = asPermission(authRequest)
          val (jwt, user) = jwtUser
          implicit val decoder = jsonOf[F, Json]
          val queryParams: Map[String, Seq[String]] = authRequest.req.multiParams
          permissions.isPermitted(user, requiredPermission).flatMap {
            case true =>
              RecordVersion.parseFromQueryParams(CrudServicesAnyCollection.VersionQueryParam, queryParams) match {
                case Valid(version) =>
                  for {
                    response <- matchEntity.read(RecordCoords(collectionName, id, version))
                    resp <- ok(response, jwt, dsl)
                  } yield resp
                case Invalid(err) =>
                  val nope = badRequest[F](s"Invalid '${CrudServicesAnyCollection.VersionQueryParam}' query parameter: ${err}")
                  setToken(nope, jwt)
              }
            case false => setToken(notAuthorised(dsl.Unauthorized, requiredPermission), jwt)
          }
      }
    }
  }
}
