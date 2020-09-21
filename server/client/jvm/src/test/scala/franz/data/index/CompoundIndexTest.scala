package franz.data.index

import cats.data.Kleisli
import cats.effect.IO
import franz.data.VersionedRecord
import franz.data.VersionedRecord.syntax._
import franz.data.index.CompoundIndexTest.{Address, Person}
import franz.rest.Swagger
import franz.rest.Swagger.PostRequest
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class CompoundIndexTest extends AnyWordSpec with Matchers {

  "CompoundIndex.nest" should {
    "place the json at the particular path" in {
      CompoundIndex.nestJson(List("a", "b", "c"), "value".asJson).noSpaces shouldBe """{"a":{"b":{"c":"value"}}}"""
    }
  }

  "CompoundIndex.apply" should {
    "compute a merged index" in {
      val underTest: MergeValues = MergeValues(
        Seq(
          MergeValues.SelectPath(List("address", "line1"), StemOp.LowerCase),
          MergeValues.SelectPath(List("address", "city"), StemOp.Trim(true), StemOp.LowerCase),
          MergeValues.SelectPath(List("address", "zipCode"), StemOp.AlphaNum)
        ),
        List("address", "oneLine")
      )
      val json = underTest.apply(Person("dave", Address("123 Main St", "Green Bay", "99 888")))
      val Some(oneLine) = json.hcursor.downField("address").downField("oneLine").focus.flatMap(_.asString)
      oneLine shouldBe "123 main st;greenbay;99888"
    }
    "compute an expanded index" in {
      val op = SplitOp.SplitString(" ").andThen(StemOp.LowerCase).andThen(SplitOp.Fixed("jonathan" -> Seq("john", "jon", "jonathan")))
      val underTest = ExpandValues(List("name"), List("expanded"), op)
      val jonathan = underTest.apply(Person("Jonathan", Address("123 Main St", "Green Bay", "99 888")))
      val Some(expanded) = jonathan.hcursor.downField("expanded").focus
      expanded.as[Seq[String]].toTry.get should contain only("john", "jon", "jonathan")

      val steve = underTest.apply(Person("Steve Smith", Address("123 Main St", "Green Bay", "99 888")))
      val Some(steveExpanded) = steve.hcursor.downField("expanded").focus
      steveExpanded.as[Seq[String]].toTry.get should contain only("steve", "smith")
    }
  }
}

object CompoundIndexTest {

  case class Address(line1: String, city: String, zipCode: String)

  case class Person(name: String, address: Address)

}
