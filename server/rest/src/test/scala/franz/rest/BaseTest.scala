package franz.rest

import java.util.UUID

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

abstract class BaseTest extends AnyWordSpec with Matchers with GivenWhenThen {

  implicit val rt = zio.Runtime.default

  def nextTopic() = s"topic${UUID.randomUUID().toString}".filter(_.isLetterOrDigit)

}
