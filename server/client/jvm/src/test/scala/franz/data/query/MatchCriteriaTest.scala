package franz.data.query

import io.circe.syntax._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class MatchCriteriaTest extends AnyWordSpec with Matchers {

  "MatchCriteria.reverse" should {
    "swap the left and right sides (but not the conjunctions)" in {
      import MatchCriteria.syntax._
      val original = "a".equalTo("b") and ("c" equalTo "d") or ("e" equalTo "f").not
      val expected = "b".equalTo("a") and ("d" equalTo "c") or ("f" equalTo "e").not
      original.reverse shouldBe expected

    }
  }
  "MatchCriteria" should {
    "be serializable to json" in {
      val equals: MatchCriteria = MatchCriteria.equals("a", "b")
      val in: MatchCriteria = MatchCriteria.in("a", "b")

      equals.asJson should not be (in.asJson)
      equals.asJson.as[MatchCriteria] shouldBe Right(equals)
      in.asJson.as[MatchCriteria] shouldBe Right(in)
    }
  }
}
