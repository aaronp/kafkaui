package franz.rest

import args4c.implicits._
import com.typesafe.scalalogging.StrictLogging
import franz.ui.routes.StaticFileRoutes
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import zio.{Task, URIO, ZEnv}
import zio.interop.catz._
import zio.interop.catz.implicits._
import cats.effect.ExitCode

import scala.concurrent.ExecutionContext


object MainZIO extends CatsApp with StrictLogging {

  object example {

    import org.http4s.circe.CirceEntityCodec._
    import zio.Task
    import zio.interop.catz._

    val dsl: Http4sDsl[Task] = Http4sDsl[Task]

    import dsl._

    def getBooks(): Task[List[String]] = {
      Task(List("a", "b"))
    }

    val routes = HttpRoutes.of[Task] {
      case _@GET -> Root / "books" =>
        println("getting books...")
        getBooks().flatMap { books: List[String] =>
          println(s"got ${books.size} books...")
          Ok(books)
        }
    }
  }

  override def run(args: List[String]): URIO[ZEnv, zio.ExitCode] = {
    runForArgs(args).map {
      case code => zio.ExitCode.apply(code.code)
    }
  }

  def runForArgs(args: List[String]): URIO[Any, ExitCode] = {
    val config = args.toArray.asConfig().resolve()
    logger.info(s"\nStarting with\n${config.summary()}\n")

    val host = config.getString("franz.www.host")
    val port = config.getInt("franz.www.port")

    val staticRoutes: StaticFileRoutes = StaticFileRoutes(config)
    val httpRoutes = Router[Task](
      "/rest" -> example.routes,
      "/" -> staticRoutes.routes[Task]()
    ).orNotFound

    BlazeServerBuilder[Task](ExecutionContext.global)
      .bindHttp(port, host)
      .withHttpApp(httpRoutes)
      .serve
      .compile[Task, Task, ExitCode]
      .drain
      .fold(_ => ExitCode.Error, _ => ExitCode.Success)
  }
}
