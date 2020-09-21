package franz.test.steps

import com.typesafe.scalalogging.StrictLogging
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Span}

import scala.concurrent.duration._

abstract class IntegrationAssertions extends StrictLogging with Matchers with ScalaFutures with Eventually {

  def testTimeout: FiniteDuration = 5.seconds

  override implicit def patienceConfig = PatienceConfig(timeout = Span(testTimeout.toMillis, Millis), interval = Span(500, Millis))


}
