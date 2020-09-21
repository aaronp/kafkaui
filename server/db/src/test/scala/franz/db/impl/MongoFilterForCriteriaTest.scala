package franz.db.impl

import franz.data.query.{Join, MatchCriteria}
import franz.db.impl.TestData.{DifferentRecord, SomeRecord}
import io.circe.literal._
import mongo4m.BsonUtil
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class MongoFilterForCriteriaTest extends AnyWordSpec with Matchers {

  "MongoFilterForCriteria.mongoFilterForQuery" should {
    "reference the field from the right-hand-side property and the value from the left-hand-side property" in {
      val query = Join[SomeRecord, DifferentRecord](MatchCriteria.equals("counter", "number"))
      val refData = TestData.SomeRecord(counter = 7890)
      val criteria: Bson = MongoFilterForCriteria.mongoFilterForQuery(query, refData)

      criteria shouldBe Filters.eq("number", BsonUtil.asBson(json"7890"))
    }
  }
}
