package franz.data.query

import franz.data.VersionedRecord
import io.circe.Json
import io.circe.parser.decode
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.util.Success

class VersionedRecordTest extends AnyWordSpec with Matchers {

  "VersionedRecord from json" should {
    "decode json" in {
      val fromJson =
        """{
          |  "_id" : {
          |    "$$oid" : "5e28cc243e714c36a3212b4c"
          |  },
          |  "data" : "some record",
          |  "version" : 2,
          |  "id" : "bar1579732004114",
          |  "userId" : "dave",
          |  "createdEpochMillis" : 1579732004114
          |}""".stripMargin


      val Success(backAgain) = decode[VersionedRecord[Json]](fromJson).toTry
      backAgain.id shouldBe "bar1579732004114"
    }
  }
}
