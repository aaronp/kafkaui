package franz.data.crud.routes

import cats.effect.Sync
import cats.implicits._
import franz.data.crud.Search
import franz.users.routes.{asPermission, notAuthorised, ok, setToken}
import franz.users.{PermissionPredicate, WebUser}
import org.http4s.AuthedRoutes
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl

object SearchRoutes {

  def apply[F[_] : Sync](service: Search.Service[F],
                         permissions: PermissionPredicate[F],
                         dsl: Http4sDsl[F] = Http4sDsl[F]): AuthedRoutes[WebUser, F] = {
    import dsl._

    implicit val decoder = jsonOf[F, Search.Request]

    AuthedRoutes.of {
      case authRequest@POST -> Root / "search" as _ =>
        val (jwt, user) = authRequest.context

        val requiredPermission = asPermission(authRequest)
        permissions.isPermitted(user, requiredPermission).flatMap {
          case true =>
            for {
              body <- authRequest.req.as[Search.Request]
              response <- service.search(body)
              resp <- ok(response, jwt, dsl)
            } yield resp
          case false => setToken(notAuthorised(dsl.Unauthorized, requiredPermission), jwt)
        }
    }
  }
}
