package franz.rest.routes

import com.typesafe.config.Config
import org.http4s.HttpRoutes
import org.http4s.server.middleware.{CORS, CORSConfig}
import zio.Task
import zio.interop.catz._
import cats.implicits._
import scala.concurrent.duration._

object RestRoutes {

  def apply(config: Config)(implicit runtime: EnvRuntime): HttpRoutes[Task] = {

    val appRoutes = {
      ConfigApp(config).routes <+> KafkaApp(config).routes
    }

    // ATM cors is just on/off
    if (config.getBoolean("franz.www.cors.allowAll")) {
      val corsConfig = CORSConfig(
        anyOrigin = true,
        anyMethod = false,
        allowedMethods = Some(Set("GET", "POST")),
        //        allowedOrigins = Set("https://foo"),
        allowCredentials = true,
        maxAge = 1.day.toSeconds)
      CORS(appRoutes, corsConfig)
    } else {
      appRoutes
    }
  }

}
