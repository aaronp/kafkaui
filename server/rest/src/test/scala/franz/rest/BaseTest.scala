package franz.rest

import java.util.UUID

import org.scalatest.GivenWhenThen
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import zio.{CancelableFuture, Task, ZIO}

import concurrent.duration._
import org.scalatest.time.{Millis, Seconds, Span}

abstract class BaseTest extends AnyWordSpec with Matchers with GivenWhenThen with Eventually with ScalaFutures {

  implicit val rt = zio.Runtime.default


  def testTimeout: FiniteDuration = 15.seconds
  def testTimeoutJava = java.time.Duration.ofMillis(testTimeout.toMillis)
  def shortTimeoutJava = java.time.Duration.ofMillis(200)

  implicit override def patienceConfig = PatienceConfig(timeout = scaled(Span(testTimeout.toSeconds, Seconds)), interval = scaled(Span(150, Millis)))

  def nextTopic() = s"topic${UUID.randomUUID().toString}".filter(_.isLetterOrDigit)

  implicit def asRichZIO[A](zio: => Task[A]) = new {
    def value(): A = rt.unsafeRun(zio)
    def valueFuture(): CancelableFuture[A] = rt.unsafeRunToFuture(zio)
  }
}
