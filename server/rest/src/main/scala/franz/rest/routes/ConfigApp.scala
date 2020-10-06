package franz.rest.routes

import cats.implicits._
import com.typesafe.config.{Config, ConfigRenderOptions}
import eie.io._
import franz.rest.ConfigService
import org.http4s.HttpRoutes
import zio.Task
import zio.interop.catz._

/**
 * routes for serv
 */
case class ConfigApp(config: Config) {
  val dataDir = config.getString("franz.data.dir").asPath.mkDirs()

  def routes(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
    val get: HttpRoutes[Task] = getConfigRoute(runtime)
    val post: HttpRoutes[Task] = saveConfigRoute(runtime)

    get <+> post
  }

  def getConfigRoute(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
    ConfigRoute.configForName {
      case None | Some("default") => pathForName("default").flatMap {
        case None => Task.some(config.root.render(ConfigRenderOptions.concise()))
        case ok => Task.succeed(ok)
      }
      case Some(name) => pathForName(name)
    }
  }

  def saveConfigRoute(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
    ConfigRoute.saveConfig(ConfigService.save(dataDir))
  }

  private def pathForName(name: String): Task[Option[String]] = Task {
    val path = dataDir.resolve(name)
    if (path.exists()) {
      path.text.some.filterNot(_.isEmpty)
    } else {
      None
    }
  }
}
