package franz.rest

import args4c.implicits._
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import franz.rest.kafka.routes.ProducerOps
import franz.ui.routes.StaticFileRoutes
import org.http4s.HttpRoutes
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import zio._
import zio.interop.catz._
import zio.interop.catz.implicits._

import scala.concurrent.ExecutionContext


object MainZIO extends CatsApp with StrictLogging {

  override def run(args: List[String]): URIO[ZEnv, ExitCode] = {
    val config = args.toArray.asConfig().resolve()
    logger.info(s"\nStarting with\n${config.summary()}\n")
    Task(ProducerOps(config))
      .bracket(svc => UIO(svc.close()), svc => runWith(svc, config))
      .either
      .map {
        case Left(err) =>
          logger.error(s"Error starting up: ${err.getMessage}", err)
          ExitCode.failure
        case Right(exitCode) => exitCode
      }
  }

  private def runWith(producer: ProducerOps, config: Config): ZIO[Any, Throwable, ExitCode] = {
    val host = config.getString("franz.www.host")
    val port = config.getInt("franz.www.port")

    val logHeaders = config.getBoolean("franz.www.logHeaders")
    val logBody = config.getBoolean("franz.www.logBody")

    def mkRouter(restRoutes : HttpRoutes[Task]) = {
      val httpApp = org.http4s.server.Router[Task](
        "/rest" -> restRoutes,
        "/" -> StaticFileRoutes(config).routes[Task]()
      ).orNotFound
      if (logHeaders || logBody) {
        Logger.httpApp(logHeaders, logBody)(httpApp)
      } else httpApp
    }

    for {
      restRoutes <- RestRoutes(config).provide(producer)
      httpRoutes = mkRouter(restRoutes)
      exitCode <- BlazeServerBuilder[Task](ExecutionContext.global)
        .bindHttp(port, host)
        .withHttpApp(httpRoutes)
        .serve
        .compile[Task, Task, cats.effect.ExitCode]
        .drain
        .fold(_ => ExitCode.failure, _ => ExitCode.success)
    } yield exitCode
  }
}
