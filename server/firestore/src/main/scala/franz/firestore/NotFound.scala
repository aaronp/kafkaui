package franz.firestore

import java.util.concurrent.ExecutionException
import com.google.api.gax.rpc.NotFoundException

object NotFound {
  def unapply(t: Throwable): Option[NotFoundException] = {
    t match {
      case exp: NotFoundException => Some(exp)
      case exp: ExecutionException => unapply(exp.getCause)
      case _ => None
    }
  }
}
