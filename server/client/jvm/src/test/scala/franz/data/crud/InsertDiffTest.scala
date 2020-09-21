package franz.data.crud

import io.circe.Json
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class InsertDiffTest extends AnyWordSpec with Matchers with GivenWhenThen {

  "concat" should {
    "work" in {
      import donovan.implicits._
      val first = "foo.first".asJPath.asExpression
      val last = "foo.last".asJPath.asExpression
      val expr = first.concat(last)

      import io.circe.syntax._
      val input = Json.obj("foo" -> Json.obj(
        "first" -> "Aaron".asJson,
        "last" -> "Pritzlaff".asJson
      ))

      val output = expr.eval(input)
      output shouldBe Some("AaronPritzlaff".asJson)


    }
  }
}
