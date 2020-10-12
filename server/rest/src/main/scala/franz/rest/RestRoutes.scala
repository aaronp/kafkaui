package franz.rest

import cats.implicits._
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import franz.rest.MainZIO.logger
import franz.rest.config.routes.ConfigApp
import franz.rest.kafka.routes.{KafkaApp, ProducerOps}
import io.circe.Json
import io.circe.syntax._
import org.http4s.{HttpRoutes, Response, Status}
import org.http4s.server.middleware.{CORS, CORSConfig}
import zio.interop.catz._
import zio.{Task, ZIO}

import org.http4s.circe.CirceEntityCodec._
import scala.concurrent.duration._
import scala.util.Try

object RestRoutes extends StrictLogging {

  def apply(config: Config = ConfigFactory.load())(implicit runtime: EnvRuntime): ZIO[ProducerOps, Throwable, HttpRoutes[Task]] = {
    KafkaApp(config).routes.map { kafkaRoutes: HttpRoutes[Task] =>

      val appRoutes = ErrorHandler(ConfigApp(config).routes <+> kafkaRoutes) {
        case (request, exp) => Task {
          logger.error(s"Error handling ${request}: $exp", exp)
          val body = Json.obj(
            "request" -> request.toString.asJson,
            "error" -> Try(exp.getMessage).getOrElse("" + exp).asJson)

          /**
           * This is a hack/work-around for the futter http package which
           * swallows the body for non-successful responses
           */
          Response(Status(210, "Server Error")).withEntity(body)
        }
      }

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
      anyMethod = true,
      //      allowedMethods = Some(Set("GET", "POST", "DELETE")),
      //        allowedOrigins = Set("https://foo"),
      allowCredentials = true,
      maxAge = 100.days.toSeconds)
    CORS(appRoutes, corsConfig)
  }

}
