package franz.rest

import cats.implicits._
import com.typesafe.config.Config
import franz.rest.config.routes.ConfigApp
import franz.rest.kafka.routes.{KafkaApp, ProducerOps}
import org.http4s.HttpRoutes
import org.http4s.server.middleware.{CORS, CORSConfig}
import zio.interop.catz._
import zio.{Task, ZIO}

import scala.concurrent.duration._

object RestRoutes {

  def apply(config: Config)(implicit runtime: EnvRuntime): ZIO[ProducerOps, Throwable, HttpRoutes[Task]] = {
    //  def apply(config: Config)(implicit runtime: EnvRuntime) = {
    KafkaApp(config).routes.map { kafkaRoutes =>
      val appRoutes = ConfigApp(config).routes <+> kafkaRoutes

      // ATM cors is just on/off
      if (config.getBoolean("franz.www.cors.allowAll")) {
        withCors(config, appRoutes)
      } else {
        appRoutes
      }
    }
  }

  def withCors(config: Config, appRoutes: HttpRoutes[Task])(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
    val corsConfig = CORSConfig(
      anyOrigin = true,
      anyMethod = false,
      allowedMethods = Some(Set("GET", "POST", "DELETE")),
      //        allowedOrigins = Set("https://foo"),
      allowCredentials = true,
      maxAge = 1.day.toSeconds)
    CORS(appRoutes, corsConfig)
  }

}
