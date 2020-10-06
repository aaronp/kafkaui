package franz.rest.routes

import franz.rest.ConfigService
import org.http4s.dsl.io.OptionalQueryParamDecoderMatcher
import org.http4s._
import zio.interop.catz._
import zio.{Task, UIO, ZIO}


/**
 * We save kafka configs against a name on the server-size
 */
object ConfigRoute {


  object ConfigNameParam extends OptionalQueryParamDecoderMatcher[String]("name")

  /**
   *
   * @param configLookup the lookup function
   */
  def configForName(configLookup: Option[String] => Task[Option[String]])(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
    import taskDsl._
    HttpRoutes.of[Task] {
      case _@GET -> Root / "config" :? ConfigNameParam(configName) =>
        val response: ZIO[Any, Throwable, Response[Task]] = configLookup(configName).flatMap {
          case Some(config) =>
            implicit val ee = EntityEncoder.stringEncoder
            println()
            println(config)
            println()
            Task(Response(Status.Ok).withEntity(config))
          case None =>
            // import org.http4s.circe.CirceEntityCodec._
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
