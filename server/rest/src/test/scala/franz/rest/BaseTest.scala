package franz.rest

import java.util.UUID

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import zio.ZIO
import concurrent.duration._

abstract class BaseTest extends AnyWordSpec with Matchers with GivenWhenThen {

  implicit val rt = zio.Runtime.default

  def testTimeout = 5.seconds

  def nextTopic() = s"topic${UUID.randomUUID().toString}".filter(_.isLetterOrDigit)

  implicit def asRichZIO[E, A](zio: => ZIO[Any, E, A]) = new {
    def value(): A = rt.unsafeRun(zio)
  }
}
