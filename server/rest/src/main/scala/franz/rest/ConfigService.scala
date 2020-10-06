package franz.rest

import java.nio.file.Path

import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}
import zio.{Task, UIO, ZIO}

import scala.util.Try

object ConfigService {

  private val AllowedChars = "-_.".toSet

  def nameIsOk(configName: String) = {
    configName.nonEmpty &&
      !configName.contains("..") &&
      configName.forall { c =>
        c.isLetterOrDigit || AllowedChars.contains(c)
      }
  }


  final case class SaveRequest(name: String, configText: String) {
    def asConfig: Try[Config] = Try(ConfigFactory.parseString(configText))
  }

  def save(dataDir: Path)(request: SaveRequest): UIO[Either[String, Unit]] = {
    import eie.io._
    val saveTask: ZIO[Any, Throwable, Unit] = for {
      okConfig <- Task.fromTry(request.asConfig)
      _ <- Task(dataDir.resolve(request.name).text = okConfig.root.render(ConfigRenderOptions.concise().setJson(true)))
    } yield ()

    saveTask.either.map {
      case Left(err) => Left(s"Error saving '${request.name}': ${err.getMessage}")
      case Right(right) => Right(right)
    }
  }
}
