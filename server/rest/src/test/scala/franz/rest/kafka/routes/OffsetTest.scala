package franz.rest.kafka.routes

import franz.rest.BaseTest
import io.circe.syntax._

class OffsetTest extends BaseTest {

  "Offset" should {
    Seq[Offset](Earliest, Latest, Timestamp(123)).foreach { expected =>
      s"encode $expected to/from json" in {
        val json = expected.asJson
        json.as[Offset] shouldBe Right(expected)
      }
    }
  }
}
