package franz.firestore

import com.google.api.core.ApiFuture
import zio.blocking.Blocking
import zio.{UIO, ZIO}

object ApiFutureTask {
  def apply[A](future: ApiFuture[A]): ZIO[Blocking, Throwable, A] = {
    zio.blocking.effectBlockingCancelable(future.get())(UIO(future.cancel(true)))
  }
}
