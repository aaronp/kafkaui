package franz.rest.config.routes

import com.typesafe.config.{Config, ConfigFactory}
import franz.rest.EnvRuntime
import org.http4s.HttpRoutes
import zio.Task
import eie.io._
import zio.interop.catz._
import cats.implicits._


/**
 * Wires in the config services w/ the routes
 */
case class ConfigApp(config: Config) {
  val dataDir = config.getString("franz.data.dir").asPath.mkDirs()

  /**
   * @param runtime
   * @return the configuration routes
   */
  def routes(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
    val get: HttpRoutes[Task] = getConfigRoute(runtime)
    val post: HttpRoutes[Task] = saveConfigRoute(runtime)

    get <+> post
  }

  def getConfigRoute(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
    ConfigRoute.configForName {
      case None | Some("default") => pathForName("default").flatMap {
        case None => Task.some(config)
        case ok => Task.succeed(ok)
      }
      case Some(name) => pathForName(name)
    }
  }

  def saveConfigRoute(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
    ConfigRoute.saveConfig(ConfigService.save(dataDir))
  }

  private def pathForName(name: String): Task[Option[Config]] = Task {
    val path = dataDir.resolve(name)
    if (path.exists()) {
      path.text.some.filterNot(_.isEmpty).map { cfgText =>
        ConfigFactory.parseString(cfgText)
      }
    } else {
      None
    }
  }
}
