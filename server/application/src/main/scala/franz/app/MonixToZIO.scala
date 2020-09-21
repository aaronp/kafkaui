package franz.app

import cats.arrow.FunctionK
import monix.eval
import monix.execution.Scheduler
import zio._

case class MonixToZIO(scheduler: Scheduler) extends FunctionK[monix.eval.Task, zio.Task] {
  override def apply[A](fa: eval.Task[A]): Task[A] = {
    Task.fromFuture(_ => fa.runToFuture(scheduler))
  }
}
