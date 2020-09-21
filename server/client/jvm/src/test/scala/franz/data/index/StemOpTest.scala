package franz.data.index

import io.circe.syntax._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class StemOpTest extends AnyWordSpec with Matchers {

  "StemOp.RegexReplace" should {
    "replace the first match" in {
      StemOp.RegexReplace("ma+", "x", true)("alpha gammaaa gammaaaaa") shouldBe "alpha gamx gamx"
      StemOp.RegexReplace("ma+", "x", false)("alpha gammaaa gammaaa") shouldBe "alpha gamx gammaaa"
    }
  }
  "StemOp.RegexFindFirst" should {
    "return the first match" in {
      StemOp.RegexFindFirst("g.*")("alpha gamma beta") shouldBe "gamma beta"
    }
  }
  "StemOp" should {
    List[StemOp](
      StemOp.TakeLeft(1),
      StemOp.TakeRight(2),
      StemOp.Trim(true),
      StemOp.Trim(false),
      StemOp.RegexReplace("a+", "A"),
      StemOp.RegexFindFirst("a+"),
      StemOp.AlphaNum,
      StemOp.Prepend("prefix"),
      StemOp.UpperCase,
      StemOp.LowerCase
    ).foreach { op =>
      s"serialize $op to/from json" in {
        val json = op.asJson
        json.as[StemOp] shouldBe Right(op)
      }
    }
  }
}
