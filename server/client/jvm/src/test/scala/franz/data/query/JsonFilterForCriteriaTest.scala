package franz.data.query

import franz.data.{MoreTestData, SomeTestClass}
import io.circe.Json
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class JsonFilterForCriteriaTest extends AnyWordSpec with Matchers {

  val textValue = Json.fromString("text value")
  val oneHundred = Json.fromInt(100)
  val twoHundred = Json.fromInt(200)

  def json1 = {
    Json.obj(
      "number" -> Json.fromInt(123),
      "x" -> oneHundred,
      "null" -> Json.Null,
      "textValue" -> textValue,
      "array" -> Json.fromValues(Seq(oneHundred, textValue))
    )
  }

  def json2 = {
    Json.obj(
      "oneHundred" -> oneHundred,
      "twoHundred" -> twoHundred,
      "text" -> textValue,
      "nil" -> Json.Null,
      "matchingArray" -> Json.fromValues(Seq(oneHundred, textValue)),
      "reversedArray" -> Json.fromValues(Seq(textValue, oneHundred)),
      "repeatedArray" -> Json.fromValues(Seq(oneHundred, oneHundred, textValue, textValue)),
      "singleArray" -> Json.fromValues(Seq(textValue)),
      "mixedArray" -> Json.fromValues(Seq(textValue, twoHundred)),
      "uniqueArray" -> Json.fromValues(Seq(twoHundred))
    )
  }

  def returnsTrue = MatchCriteria.equals("x", "oneHundred")

  def returnsTrue2 = MatchCriteria.equals("textValue", "text")

  def returnsFalse = MatchCriteria.equals("x", "nil")

  "JsonFilterForCriteria.createFilterFor" should {

    "produce a predicate which can be used to match data" in {
      val criteria = MatchCriteria.equals("number", "integer")
      val data = SomeTestClass("foo", 567, false)
      val filter: MoreTestData => Boolean = JsonFilterForCriteria.createFilterFor(criteria, data)
      filter(MoreTestData("bar", 122)) shouldBe false
      filter(MoreTestData("bar", 567)) shouldBe true
    }
  }
  "JsonFilterForCriteria.and" should {
    "and together two criteria" in {
      JsonFilterForCriteria(returnsTrue.and(returnsFalse), json1, json2) shouldBe false
      JsonFilterForCriteria(returnsFalse.and(returnsTrue), json1, json2) shouldBe false
      JsonFilterForCriteria(returnsTrue2.and(returnsTrue), json1, json2) shouldBe true
    }
  }
  "JsonFilterForCriteria.or" should {
    "or together two criteria" in {
      JsonFilterForCriteria(returnsTrue.or(returnsFalse), json1, json2) shouldBe true
      JsonFilterForCriteria(returnsFalse.or(returnsTrue), json1, json2) shouldBe true
      JsonFilterForCriteria(returnsTrue2.or(returnsTrue), json1, json2) shouldBe true
      JsonFilterForCriteria(returnsFalse.or(returnsFalse), json1, json2) shouldBe false
    }
  }
  "JsonFilterForCriteria.not" should {
    "negate criteria" in {
      JsonFilterForCriteria(returnsTrue, json1, json2) shouldBe true
      JsonFilterForCriteria(returnsTrue.not, json1, json2) shouldBe false
      JsonFilterForCriteria(returnsFalse, json1, json2) shouldBe false
      JsonFilterForCriteria(returnsFalse.not, json1, json2) shouldBe true
    }
  }
  "JsonFilterForCriteria.in" should {
    "1.a) left value is an array, right value isn't when MatchOneValue(strict = false) should match 'if any value in the left array matches the right value that's a match'" in {
      JsonFilterForCriteria(MatchCriteria.in("array", "twoHundred", In.Strategy.matchOne(false)), json1, json2) shouldBe false
      JsonFilterForCriteria(MatchCriteria.in("array", "oneHundred", In.Strategy.matchOne(false)), json1, json2) shouldBe true
      JsonFilterForCriteria(MatchCriteria.in("array", "text", In.Strategy.matchOne(false)), json1, json2) shouldBe true
    }
    "1.b) left value is an array, right value isn't when MatchOneValue(strict = true) should not match as they're different types" in {
      JsonFilterForCriteria(MatchCriteria.in("array", "twoHundred", In.Strategy.matchOne(true)), json1, json2) shouldBe false
      JsonFilterForCriteria(MatchCriteria.in("array", "oneHundred", In.Strategy.matchOne(true)), json1, json2) shouldBe false
      JsonFilterForCriteria(MatchCriteria.in("array", "text", In.Strategy.matchOne(true)), json1, json2) shouldBe false
    }
    "2.a) left value is an array, right value is an array with strategy MatchOneValue" in {
      JsonFilterForCriteria(MatchCriteria.in("array", "matchingArray", In.Strategy.matchOne(true)), json1, json2) shouldBe true
      JsonFilterForCriteria(MatchCriteria.in("array", "reversedArray", In.Strategy.matchOne(true)), json1, json2) shouldBe true
      JsonFilterForCriteria(MatchCriteria.in("array", "reversedArray", In.Strategy.matchOne(false)), json1, json2) shouldBe true
      JsonFilterForCriteria(MatchCriteria.in("array", "repeatedArray", In.Strategy.matchOne(false)), json1, json2) shouldBe true
      JsonFilterForCriteria(MatchCriteria.in("array", "singleArray", In.Strategy.matchOne(false)), json1, json2) shouldBe true
      JsonFilterForCriteria(MatchCriteria.in("array", "mixedArray", In.Strategy.matchOne(false)), json1, json2) shouldBe true
      JsonFilterForCriteria(MatchCriteria.in("array", "uniqueArray", In.Strategy.matchOne(false)), json1, json2) shouldBe false
    }
    "2.b) left value is an array, right value is an array with strategy  MatchAllValues" in {
      // strict doesn't matter (or shouldn't matter) for these tests
      JsonFilterForCriteria(MatchCriteria.in("array", "matchingArray", In.Strategy.matchAll(true)), json1, json2) shouldBe true
      JsonFilterForCriteria(MatchCriteria.in("array", "reversedArray", In.Strategy.matchAll(true)), json1, json2) shouldBe true
      JsonFilterForCriteria(MatchCriteria.in("array", "reversedArray", In.Strategy.matchAll(false)), json1, json2) shouldBe true
      JsonFilterForCriteria(MatchCriteria.in("array", "repeatedArray", In.Strategy.matchAll(false)), json1, json2) shouldBe true
      JsonFilterForCriteria(MatchCriteria.in("array", "singleArray", In.Strategy.matchAll(true)), json1, json2) shouldBe false
      JsonFilterForCriteria(MatchCriteria.in("array", "mixedArray", In.Strategy.matchAll(true)), json1, json2) shouldBe false
      JsonFilterForCriteria(MatchCriteria.in("array", "uniqueArray", In.Strategy.matchAll(true)), json1, json2) shouldBe false
    }
    "3.a) left is not an array, right value is an array with any strategy" in {
      JsonFilterForCriteria(MatchCriteria.in("x", "matchingArray", In.Strategy.matchAll(true)), json1, json2) shouldBe true
      JsonFilterForCriteria(MatchCriteria.in("x", "matchingArray", In.Strategy.matchOne(true)), json1, json2) shouldBe true
      JsonFilterForCriteria(MatchCriteria.in("x", "matchingArray", In.Strategy.matchOne(false)), json1, json2) shouldBe true
      JsonFilterForCriteria(MatchCriteria.in("textValue", "mixedArray", In.Strategy.matchOne(true)), json1, json2) shouldBe true
      JsonFilterForCriteria(MatchCriteria.in("x", "mixedArray", In.Strategy.matchOne(true)), json1, json2) shouldBe false
      JsonFilterForCriteria(MatchCriteria.in("x", "uniqueArray", In.Strategy.matchOne(true)), json1, json2) shouldBe false
      JsonFilterForCriteria(MatchCriteria.in("x", "uniqueArray", In.Strategy.matchOne(false)), json1, json2) shouldBe false
      JsonFilterForCriteria(MatchCriteria.in("x", "uniqueArray", In.Strategy.matchAll(true)), json1, json2) shouldBe false
    }
    "4.a) left is not an array, right is not an array with strict strategy should not match" in {
      JsonFilterForCriteria(MatchCriteria.in("x", "oneHundred", In.Strategy.matchAll(false)), json1, json2) shouldBe true
      JsonFilterForCriteria(MatchCriteria.in("x", "oneHundred", In.Strategy.matchOne(false)), json1, json2) shouldBe true
      JsonFilterForCriteria(MatchCriteria.in("x", "oneHundred", In.Strategy.matchAll(true)), json1, json2) shouldBe false
      JsonFilterForCriteria(MatchCriteria.in("x", "oneHundred", In.Strategy.matchOne(true)), json1, json2) shouldBe false
    }
    "4.b) left is not an array, right is not an array with non-strict strategy should fall back to equals comparison" in {
      JsonFilterForCriteria(MatchCriteria.in("x", "oneHundred", In.Strategy.matchAll(false)), json1, json2) shouldBe true
      JsonFilterForCriteria(MatchCriteria.in("x", "oneHundred", In.Strategy.matchOne(false)), json1, json2) shouldBe true
    }

    "match a value which is in an array" in {
      JsonFilterForCriteria(MatchCriteria.in("x", "matchingArray"), json1, json2) shouldBe true
      JsonFilterForCriteria(MatchCriteria.in("x", "repeatedArray"), json1, json2) shouldBe true
      JsonFilterForCriteria(MatchCriteria.in("x", "singleArray"), json1, json2) shouldBe false
    }
    "match an array if there is one value within the other array" in {
      JsonFilterForCriteria(MatchCriteria.in("array", "matchingArray"), json1, json2) shouldBe true
      JsonFilterForCriteria(MatchCriteria.in("array", "reversedArray"), json1, json2) shouldBe true
      JsonFilterForCriteria(MatchCriteria.in("array", "repeatedArray"), json1, json2) shouldBe true
      JsonFilterForCriteria(MatchCriteria.in("array", "singleArray"), json1, json2) shouldBe true
    }
  }

  "JsonFilterForCriteria.equals" should {
    "match simple equality clauses" in {
      JsonFilterForCriteria(MatchCriteria.equals("x", "oneHundred"), json1, json2) shouldBe true
      JsonFilterForCriteria(MatchCriteria.equals("null", "nil"), json1, json2) shouldBe true
      JsonFilterForCriteria(MatchCriteria.equals("textValue", "text"), json1, json2) shouldBe true
    }
    "not match simple inequality clauses" in {
      JsonFilterForCriteria(MatchCriteria.equals("x", "text"), json1, json2) shouldBe false
      JsonFilterForCriteria(MatchCriteria.equals("x", "nil"), json1, json2) shouldBe false
    }
    "match array equality clauses" in {
      JsonFilterForCriteria(MatchCriteria.equals("matchingArray", "matchingArray"), json2, json2) shouldBe true
      JsonFilterForCriteria(MatchCriteria.equals("singleArray", "singleArray"), json2, json2) shouldBe true
    }
    "not match array inequality clauses" in {
      JsonFilterForCriteria(MatchCriteria.equals("matchingArray", "singleArray"), json2, json2) shouldBe false
      JsonFilterForCriteria(MatchCriteria.equals("repeatedArray", "matchingArray"), json2, json2) shouldBe false
    }
    "not match when left or right values are missing" in {
      JsonFilterForCriteria(MatchCriteria.equals("x", "missing"), json1, json2) shouldBe false
      JsonFilterForCriteria(MatchCriteria.equals("missing", "oneHundred"), json1, json2) shouldBe false
    }
    "not match when left or right values are null" in {
      JsonFilterForCriteria(MatchCriteria.equals("null", "oneHundred"), json1, json2) shouldBe false
      JsonFilterForCriteria(MatchCriteria.equals("x", "nil"), json1, json2) shouldBe false
    }
  }
}
