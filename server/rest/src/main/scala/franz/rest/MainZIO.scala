package franz.rest

import args4c.implicits._
import com.typesafe.scalalogging.StrictLogging
import zio._
import zio.interop.catz._
import zio.interop.catz.implicits._

object MainZIO extends CatsApp with StrictLogging {
  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] = {
    val config = args.toArray.asConfig().resolve()
    logger.info(s"\nStarting with\n${config.summary()}\n")

    RestApp.runF[zio.Task](config).either.map {
      case Left(_) => 1
      case Right(exitCode) => exitCode.code
    }
  }
}
