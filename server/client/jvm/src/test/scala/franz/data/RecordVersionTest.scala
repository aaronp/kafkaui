package franz.data


import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class RecordVersionTest extends AnyWordSpec with Matchers {

  "RecordVersion" should {
    List(
      RecordVersion.latest -> LatestVersion,
      RecordVersion.previous(5) -> PreviousVersion(5),
      RecordVersion.next(5) -> NextVersion(5),
      RecordVersion(8) -> ExplicitVersion(8)
    ).foreach {
      case (input, expected) =>
        s"serialize $input to/from json" in {
          import io.circe.syntax._
          input.asJson.as[RecordVersion] shouldBe Right(expected)
        }
    }
  }
}

