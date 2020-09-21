package franz


import cats.Parallel
import cats.effect.{ConcurrentEffect, ContextShift, ExitCode, IO, IOApp}

import scala.concurrent.ExecutionContext

/**
 * The context shift/concurrent implicits required to run IO instances
 */
case class Env() extends IOApp {
  lazy val shift: ConcurrentEffect[IO] = implicitly[ConcurrentEffect[IO]]

  override def run(args: List[String]): IO[ExitCode] = IO.pure(ExitCode.Success)

  lazy val ctxtShift: ContextShift[IO] = IO.contextShift(ExecutionContext.Implicits.global)

  lazy val parallel: Parallel[IO] = IO.ioParallel

  object implicits {
    implicit def concurrentEffect = shift

    implicit def contextShift = ctxtShift

    implicit def ioPar = parallel
  }

}

