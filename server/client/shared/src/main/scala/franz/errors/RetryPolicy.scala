package franz.errors

import scala.concurrent.duration._

case class RetryPolicy(max: Int, delay: FiniteDuration) {
  require(max >= 0)

  def dec = if (isEmpty) this else copy(max = max - 1)
  def isEmpty = max == 0
  def nonEmpty = max > 0
}

object RetryPolicy {
  def quick = RetryPolicy(20, 50.millis)
  def none = RetryPolicy(0, 50.millis)
}
