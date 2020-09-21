package franz.io

import cats.effect.IO
import monix.eval.Task

trait Run[F[_]] {
  def run[A](fa: F[A]): A
}

object Run {

  def apply[F[_]](implicit instance: Run[F]): Run[F] = instance

  implicit object RunTask extends Run[Task] {

    import monix.execution.Scheduler.Implicits.global

    override def run[A](fa: Task[A]): A = {
      fa.runSyncUnsafe()
    }
  }

  implicit object RunIO extends Run[IO] {
    override def run[A](fa: IO[A]): A = {
      fa.unsafeRunSync()
    }
  }

}
