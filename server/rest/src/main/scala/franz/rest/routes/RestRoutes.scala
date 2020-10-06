package franz.rest.routes

import com.typesafe.config.Config
import org.http4s.HttpRoutes
import zio.Task

object RestRoutes {

  def apply(config: Config)(implicit runtime: EnvRuntime): HttpRoutes[Task] = {

    val configRoutes = ConfigApp(config).routes

    configRoutes
  }

}
