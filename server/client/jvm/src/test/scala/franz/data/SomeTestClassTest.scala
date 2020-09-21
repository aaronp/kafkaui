package franz.data

import franz.data.query.MatchCriteria
import io.circe.syntax._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import MatchCriteria.syntax._

class SomeTestClassTest extends AnyWordSpec with Matchers {

  "SomeTestClass.database" should {
    "be able to query data" in {
      val db = SomeTestClass.database()

      val criteria = "foo".equalTo("name")
      val found = db.read(criteria -> Map("foo" -> "Clark").asJson).unsafeRunSync()
      found.isEmpty shouldBe false
      found.foreach(_.name shouldBe "Clark")

      val notClark = db.read(criteria.not() -> Map("foo" -> "Clark").asJson).unsafeRunSync()
      notClark.isEmpty shouldBe false
      notClark.foreach(_.name should not be ("Clark"))
    }
  }
}
