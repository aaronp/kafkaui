package franz.data.index

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SplitOpTest extends AnyWordSpec with Matchers {

  "SplitOp.SplitString" should {
    "split on whitespace given \\w" in {

      val split = SplitOp.SplitString("\\W")
      val results = split("The quick   brown\tfox")
      results should contain only("The", "quick", "brown", "fox")
    }
  }
}
