package franz.rest

import cats.~>
import com.typesafe.config.Config
import franz.ui.routes.StaticFileRoutes
import org.http4s.Headers
import org.http4s.util.CaseInsensitiveString

import scala.concurrent.duration.FiniteDuration

/**
 * All the REST stuff
 *
 * @param hostPort        the server host/port (e.g. 0.0.0.0:8080)
 * @param sessionDuration the JWT session duration to use when refreshing user sessions
 * @param logHeaders      should we log HTTP headers?
 * @param logBody         should we log HTTP content?
 * @param uiRoutes        the static file routes
 * @param logAction       a logging action
 * @tparam F
 */
case class HttpSettings[F[_]](hostPort: (String, Int),
                              sessionDuration: FiniteDuration,
                              uiRoutes: StaticFileRoutes,
                              logHeaders: Boolean,
                              logBody: Boolean,
                              redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains,
                              logAction: Option[String => F[Unit]] = None) {
  def mapK[G[_]](implicit ev: F ~> G): HttpSettings[G] = {
    def mapLog(op: String => F[Unit]): String => G[Unit] = op.andThen(ev.apply)
    copy(logAction = logAction.map(mapLog))
  }
}

object HttpSettings {

  def apply[F[_]](config: Config): HttpSettings[F] = {
    import args4c.implicits._
    val host = config.getString("franz.rest.host")
    val port = config.getInt("franz.rest.port")
    val sessionDuration = config.asFiniteDuration("franz.rest.sessionDuration")
    val logHeaders = config.getBoolean("franz.www.logging.logHeaders")
    val logBody = config.getBoolean("franz.www.logging.logBody")
    val staticRoutes = StaticFileRoutes(config)
    new HttpSettings[F](host -> port, sessionDuration, staticRoutes, logHeaders, logBody)
  }
}
