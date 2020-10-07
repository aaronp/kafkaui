package franz.rest

import cats.data.{Kleisli, OptionT}
import com.typesafe.config.Config
import franz.rest.config.routes.ConfigApp
import franz.rest.kafka.routes.{KafkaApp, ProducerOps}
import org.http4s.{HttpRoutes, Request, Response}
import org.http4s.server.middleware.{CORS, CORSConfig}
import zio.{Task, ZIO}
import zio.interop.catz._
import cats.implicits._
import org.http4s.circe.CirceEntityCodec._

import concurrent.duration._

object RestRoutes {

  def apply(config: Config)(implicit runtime: EnvRuntime): ZIO[ProducerOps, Throwable, HttpRoutes[Task]] = {
    KafkaApp(config).routes.map { kafkaRoutes =>
      val appRoutes = ConfigApp(config).routes <+> kafkaRoutes
      // ATM cors is just on/off
      if (config.getBoolean("franz.www.cors.allowAll")) {
        withCords(config, appRoutes)
      } else {
        appRoutes
      }
    }
  }

  def apply2(config: Config)(implicit runtime: EnvRuntime): HttpRoutes[Task] = {

    val appRoutes = {
      ConfigApp(config).routes // <+> KafkaApp(config).routes
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

  def withCords(config: Config, appRoutes: HttpRoutes[Task])(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
    val corsConfig = CORSConfig(
      anyOrigin = true,
      anyMethod = false,
      allowedMethods = Some(Set("GET", "POST")),
      //        allowedOrigins = Set("https://foo"),
      allowCredentials = true,
      maxAge = 1.day.toSeconds)
    CORS(appRoutes, corsConfig)
  }

}
