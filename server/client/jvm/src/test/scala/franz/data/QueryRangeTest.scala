package franz.data

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class QueryRangeTest extends AnyWordSpec with Matchers {

  "QueryRange.fromQueryParams" should {
    "work" in {
      QueryRange.fromQueryParams(Map("from" -> Seq("1", "2"))) shouldBe Left("2 values specified for 'from'")
      QueryRange.fromQueryParams(Map("from" -> Seq("text"))) shouldBe Left("'from' wasn't an int: 'text'")
      QueryRange.fromQueryParams(Map("limit" -> Seq("1", "2"))) shouldBe Left("2 values specified for 'limit'")
      QueryRange.fromQueryParams(Map("limit" -> Seq("text"))) shouldBe Left("'limit' wasn't an int: 'text'")
      QueryRange.fromQueryParams(Map("from" -> Seq("4"), "limit" -> Seq("7"))) shouldBe Right(QueryRange(4, 7))
      QueryRange.fromQueryParams(Map("whatever" -> Seq("1", "2"))) shouldBe Right(QueryRange.Default)
    }
  }
}
