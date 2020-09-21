package franz.data.crud.routes

import cats.effect.Sync
import cats.implicits._
import franz.data.QueryRange
import franz.data.crud.ListRecords
import franz.users.routes._
import franz.users.{JWT, PermissionPredicate, User, WebUser}
import io.circe.Encoder
import org.http4s.AuthedRoutes
import org.http4s.dsl.Http4sDsl

object ListRecordsRoute {


  def single[F[_] : Sync, R: Encoder](singlePath: String,
                                       service: ListRecords.Service[F, R],
                                       permissions: PermissionPredicate[F]): AuthedRoutes[(JWT, User), F] = {
    apply(Seq(singlePath), service, permissions, Http4sDsl[F])
  }

  def apply[F[_] : Sync, R: Encoder](path: Seq[String],
                                      service: ListRecords.Service[F, R],
                                      permissions: PermissionPredicate[F],
                                      dsl: Http4sDsl[F] = Http4sDsl[F]): AuthedRoutes[WebUser, F] = {


    import dsl._

    val ThisPath = join(path, dsl)

    AuthedRoutes.of {
      case authRequest@GET -> ThisPath as jwtUser =>
        val (jwt, user) = jwtUser
        val requiredPermission: String = asPermission(authRequest)

        val queryParams: Map[String, Seq[String]] = authRequest.req.multiParams
        val rangeEither: Either[String, QueryRange] = QueryRange.fromQueryParams(queryParams)

        permissions.isPermitted(user, requiredPermission).flatMap {
          case true =>
            rangeEither match {
              case Left(err) => setToken(badRequest(err), jwt)
              case Right(range) =>
                for {
                  response <- service.list(range)
                  resp <- ok(response, jwt, dsl)
                } yield resp
            }
          case false => setToken(notAuthorised(dsl.Unauthorized, requiredPermission), jwt)
        }
    }
  }
}
