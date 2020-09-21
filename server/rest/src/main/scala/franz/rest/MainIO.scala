package franz.rest

import args4c.implicits._
import cats.effect.{ExitCode, IO, IOApp}
import com.typesafe.scalalogging.StrictLogging

object MainIO extends IOApp with StrictLogging {
  override def run(args: List[String]): IO[ExitCode] = {
    val config = args.toArray.asConfig().resolve()
    logger.info(s"\nStarting with\n${config.summary()}\n")
    RestApp.runF[IO](config)
  }
}
