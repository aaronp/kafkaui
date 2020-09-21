package franz.app

import args4c.implicits._
import cats.effect.ExitCode
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import franz.app.firebase.LiveFirebaseServices
import franz.app.mongo.LiveMongoServices
import franz.app.support.SupportRoutes
import franz.rest.{RestApp, RestServices}
import monix.execution.Scheduler
import org.http4s.HttpRoutes
import zio._
import zio.internal.Platform
import zio.interop.catz._
import zio.interop.catz.implicits._

import scala.util.Try

/**
 * The Main entry point
 */
object MainEntryPoint extends CatsApp with StrictLogging {
  implicit val TaskToZio: MonixToZIO = MonixToZIO(Scheduler.io("monix-app-io"))

  def argsAsConfig(userArgs: Seq[String]) = {
    val config = userArgs.toArray.asConfig().resolve()
    logger.info(s"\nResolved user args ${userArgs.mkString("[", ",", "]")} as:\n${config.summary()}\n")
    config
  }

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] = {
    implicit val platform = runtime.platform
    val env: ZIO[Any, Throwable, RestServices[Task]] = servicesForConfig(argsAsConfig(args))
    apply(env)
  }

  def servicesForConfig(config: Config)(implicit platform: Platform): Task[RestServices[Task]] = {
    val useMongo = false

    if (useMongo) {
      val record = Try(config.getBoolean("record")).getOrElse(false)
      LiveMongoServices(config, record).map(_._2)
    } else {
      LiveFirebaseServices(config)
    }
  }

  def apply(services: ZIO[Any, Throwable, RestServices[Task]]): ZIO[Any, Nothing, Int] = {
    val app: ZIO[Any, Throwable, ExitCode] = services.flatMap { input: RestServices[Task] =>

      RestApp.runF[zio.Task](input, "/support" -> SupportRoutes[zio.Task]())
    }
    app.either.map {
      case Left(_) => 1
      case Right(exitCode) => exitCode.code
    }
  }
}
