package franz.data.diff

import cats.data.Validated.{Invalid, Valid}
import franz.Env
import franz.data.{LatestVersion, PreviousVersion, RecordCoords}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class DiffRestTest extends AnyWordSpec with Matchers {

  import DiffRest.QueryParams._


  "DiffRest.QueryParams.asDiffRequest" should {
    val env = Env()
    import env.implicits._

    val left = RecordCoords("clctn", "eye-d", PreviousVersion(123))

    "error when multiple values set" in {
      val maps: Seq[Map[String, Seq[String]]] = List(
        Map(OtherCollection -> Seq("c1", "c2")),
        Map(OtherVersion -> Seq("1", "2")),
        Map(OtherId -> Seq("id1", "id2"))
      )
      maps.foreach { map =>
        val Invalid(err) = parseDiffRequest(left, map)

        withClue(err.toNonEmptyList.toList.mkString("\n")) {
          err.toNonEmptyList.toList.exists(_.contains("2 ")) shouldBe true
        }
      }

    }
    "use the left side as default when not set" in {
      import DiffRest.QueryParams._
      val left = RecordCoords("clctn", "eye-d", PreviousVersion(123))
      val Valid(actual) = parseDiffRequest(left, Map(
        OtherCollection -> Seq(),
        OtherVersion -> Seq(),
        OtherId -> Seq()
      ))
      actual shouldBe Diff.Request(left, RecordCoords("clctn", "eye-d", LatestVersion))
    }
    "parse previous version syntax" in {
      import DiffRest.QueryParams._
      val left = RecordCoords("clctn", "eye-d", LatestVersion)
      val Valid(actual) = parseDiffRequest(left, Map(
        OtherCollection -> Seq("otherColl"),
        OtherVersion -> Seq("5-"),
        OtherId -> Seq("otherId")
      ))
      actual shouldBe Diff.Request(left, RecordCoords("otherColl", "otherId", PreviousVersion(5)))
    }
  }
}
