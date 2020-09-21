package franz.users
package routes

import cats.effect.Sync
import org.http4s._
import org.http4s.dsl.Http4sDsl

object UserHealthRoute {

  def apply[F[_] : Sync]: AuthedRoutes[WebUser, F] = {
    val dsl = Http4sDsl[F]
    import dsl._

    AuthedRoutes.of {
      case GET -> Root / UserSwagger.Status as jwtUser =>
        val (jwt, user) = jwtUser
        ok(user, jwt, dsl)
    }
  }
}
