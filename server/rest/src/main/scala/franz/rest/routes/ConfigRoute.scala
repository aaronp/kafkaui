package franz.rest.routes

import com.typesafe.config.{Config, ConfigRenderOptions}
import franz.rest.ConfigService
import io.circe.Json
import org.http4s._
import org.http4s.dsl.io.OptionalQueryParamDecoderMatcher
import zio.interop.catz._
import zio.{Task, UIO, ZIO}

import scala.util.Try


/**
 * We save kafka configs against a name on the server-size
 */
object ConfigRoute {


  object ConfigNameParam extends OptionalQueryParamDecoderMatcher[String]("name")

  def configJson(config: Config): Try[Json] = {
    val json = config.root().render(ConfigRenderOptions.concise().setJson(true))
    io.circe.parser.decode[Json](json).toTry
  }

  /**
   *
   * @param configLookup the lookup function
   */
  def configForName(configLookup: Option[String] => Task[Option[Config]])(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
    import taskDsl._
    HttpRoutes.of[Task] {
      case _@GET -> Root / "config" :? ConfigNameParam(configName) =>
        val response: ZIO[Any, Throwable, Response[Task]] = configLookup(configName).flatMap {
          case Some(config) =>
            import org.http4s.circe.CirceEntityCodec._
            //            implicit val ee = EntityEncoder.stringEncoder
            for {
              c <- Task.fromTry(configJson(config))
            } yield Response(Status.Ok).withEntity(c)
          case None =>
            NotFound()
        }
        response
    }
  }

  /**
   *
   */
  def saveConfig(onSave: ConfigService.SaveRequest => UIO[Either[String, Unit]])(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
    import taskDsl._
    HttpRoutes.of[Task] {
      case req@POST -> Root / "config" / configName if ConfigService.nameIsOk(configName) =>

        val response = for {
          configBody <- EntityDecoder.decodeText(req)
          saveFnc <- Task(onSave(ConfigService.SaveRequest(configName, configBody))).either
        } yield {
          saveFnc match {
            case Left(err) => InternalServerError(s"Encountered a bug saving: '$configName': ${err.getMessage}")
            case Right(saveResult) =>
              saveResult.flatMap {
                case Left(err) => InternalServerError(s"Error saving '$configName': $err")
                case Right(_) => Ok()
              }
          }
        }
        response.flatten
      case POST -> Root / "config" / configName => BadRequest(s"Invalid config name '$configName'")
    }
  }
}
