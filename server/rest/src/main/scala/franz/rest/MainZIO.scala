package franz.rest

import args4c.implicits._
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import franz.rest.kafka.routes.ProducerServices
import franz.ui.routes.StaticFileRoutes
import org.http4s.HttpRoutes
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.server.blaze.BlazeServerBuilder
import zio._
import zio.interop.catz._
import zio.interop.catz.implicits._

import scala.concurrent.ExecutionContext


object MainZIO extends CatsApp with StrictLogging {

  override def run(args: List[String]): URIO[ZEnv, ExitCode] = {
    val config = args.toArray.asConfig().resolve()
    logger.info(s"\nStarting with\n${config.summary()}\n")
    ProducerServices(config)
      .bracket(svc => UIO(svc.close()), svc => runWith(svc, config))
      .either
      .map {
        case Left(err) =>
          logger.error(s"Error starting up: ${err.getMessage}", err)
          ExitCode.failure
        case Right(exitCode) => exitCode
      }
  }

  private def runWith(producer: ProducerServices, config: Config): ZIO[Any, Throwable, ExitCode] = {
    val host = config.getString("franz.www.host")
    val port = config.getInt("franz.www.port")

    for {
      restRoutes <- RestRoutes(config).provide(producer)
      httpRoutes: HttpRoutes[Task] = org.http4s.server.Router[Task](
        "/rest" -> restRoutes,
        "/" -> StaticFileRoutes(config).routes[Task]()
      )
      exitCode <- BlazeServerBuilder[Task](ExecutionContext.global)
        .bindHttp(port, host)
        .withHttpApp(httpRoutes.orNotFound)
        .serve
        .compile[Task, Task, cats.effect.ExitCode]
        .drain
        .fold(_ => ExitCode.failure, _ => ExitCode.success)
    } yield exitCode
  }
}
