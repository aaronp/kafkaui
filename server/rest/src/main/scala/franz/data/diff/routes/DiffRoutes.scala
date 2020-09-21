package franz.data.diff.routes

import cats.data.Validated.{Invalid, Valid}
import cats.effect.Sync
import cats.implicits._
import franz.data.{RecordCoords, RecordVersion}
import franz.data.diff.{Diff, DiffRest}
import franz.users.routes._
import franz.users.{PermissionPredicate, WebUser}
import org.http4s.AuthedRoutes
import org.http4s.circe.jsonOf
import org.http4s.dsl.Http4sDsl

case class DiffRoutes[F[_] : Sync](diffService: Diff.Service[F], permissions: PermissionPredicate[F]) {
  def routes: AuthedRoutes[WebUser, F] = getDiff <+> postDiff

  def getDiff: AuthedRoutes[WebUser, F] = DiffRoutes.getRoute[F](diffService, permissions)

  def postDiff: AuthedRoutes[WebUser, F] = DiffRoutes.postRoute[F](diffService, permissions)
}

object DiffRoutes {
  def postRoute[F[_] : Sync](service: Diff.Service[F],
                             permissions: PermissionPredicate[F], dsl: Http4sDsl[F] = Http4sDsl[F]): AuthedRoutes[WebUser, F] = {
    import dsl._

    AuthedRoutes.of {
      case authRequest@POST -> Root / DiffRest.Namespace as jwtUser =>
        val requiredPermission: String = asPermission(authRequest)
        val (jwt, user) = jwtUser

        implicit val decoder = jsonOf[F, Diff.Request]

        permissions.isPermitted(user, requiredPermission).flatMap {
          case true =>
            for {
              body <- authRequest.req.as[Diff.Request]
              response <- service.diff(body)
              resp <- ok(response, jwt, dsl)
            } yield resp
          case false => setToken(notAuthorised(dsl.Unauthorized, requiredPermission), jwt)
        }
    }
  }

  def getRoute[F[_] : Sync](service: Diff.Service[F],
                            permissions: PermissionPredicate[F], dsl: Http4sDsl[F] = Http4sDsl[F]): AuthedRoutes[WebUser, F] = {
    import dsl._

    AuthedRoutes.of {
      case authRequest@GET -> Root / DiffRest.Namespace / collectionName / id as jwtUser =>
        val requiredPermission: String = asPermission(authRequest)
        val (jwt, user) = jwtUser
        permissions.isPermitted(user, requiredPermission).flatMap {
          case true =>
            import DiffRest.QueryParams._

            val params = authRequest.req.multiParams
            RecordVersion.parseFromQueryParams(Version, params) match {
              case Invalid(err) => badRequest(err.toList.mkString(". "))
              case Valid(version) =>
                val lhs = RecordCoords(collectionName, id, version)
                parseDiffRequest(lhs, params) match {
                  case Invalid(err) => badRequest(err.toList.mkString(". "))
                  case Valid(request) => service.diff(request).flatMap(ok(_, jwt, dsl))
                }
            }
          case false => setToken(notAuthorised(dsl.Unauthorized, requiredPermission), jwt)
        }
    }
  }
}
