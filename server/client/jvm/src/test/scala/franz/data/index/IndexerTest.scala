package franz.data.index

import cats.effect.IO
import franz.Env
import franz.data.index.ReferenceToValue.RecordKey
import franz.data.{MoreTestData, SomeTestClass}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class IndexerTest extends AnyWordSpec with Matchers {

  "Indexer.index" should {
    "find associated records" in {
      val indexer = Indexer.Service[IO](4)
      val env = Env()
      import env.implicits._
      import franz.data.VersionedRecord.syntax._
      val test = for {
        _ <- indexer.index("foo", SomeTestClass(name = "alpha", number = 1).versionedRecord(id = "A1"))
        _ <- indexer.index("foo", SomeTestClass(name = "gamma", number = 20).versionedRecord(id = "A2"))
        _ <- indexer.index("bar", MoreTestData(text = "alpha", integer = 2).versionedRecord(id = "B1"))
        _ <- indexer.index("bar", MoreTestData(text = "beta", integer = 20).versionedRecord(id = "B2"))
        _ <- indexer.index("bar", MoreTestData(text = "gamma", integer = 2).versionedRecord(id = "B3"))
        matches <- indexer.valueAssociations(SomeTestClass(name = "gamma", number = 20).versionedRecord(id = "example"))
      } yield matches

      val associations: RecordAssociations = test.unsafeRunSync()
      associations.allAssociations should contain(FieldAssociation(Seq("id"), "example", None))
      associations.allAssociations should contain(FieldAssociation(Seq("version"), "0", None))
      val mbk = associations.matchesByKey

      mbk.keySet should contain only(RecordKey("foo", "A1"), RecordKey("foo", "A2"), RecordKey("bar", "B2"), RecordKey("bar", "B3"))

      val Some(mostMatchingFields: RecordMatch) = associations.bestMatchByMostAssociations
      mostMatchingFields.references should contain only(
        ReferenceToValue("foo", Seq("truthy"), "A2", 0),
        ReferenceToValue("foo", Seq("name"), "A2", 0),
        ReferenceToValue("foo", Seq("number"), "A2", 0)
      )
      mostMatchingFields.key shouldBe RecordKey("foo", "A2")

      withClue("If we weight the 'text' field in the 'bar' collection higher, that becomes the best match") {
        // if 'text' scores higher than 'name' fields, then we change our 'best' match
        val textWins = associations.bestMatches(MatchWeights.of("text" -> 10.0)).head
        textWins._1.key shouldBe RecordKey("bar", "B3")
      }

      val defaultMatch = associations.bestMatches(MatchWeights.empty).head
      defaultMatch._1.key shouldBe RecordKey("foo", "A2")
    }
  }
  "Indexer.index" should {
    "save indexes" in {
      val env = Env()
      import env.implicits._
      val indexer = Indexer.Service[IO](10)

      import franz.data.VersionedRecord.syntax._
      import indexer.BatchResult
      val test: IO[(Seq[BatchResult], Seq[BatchResult], Seq[BatchResult])] = for {
        a <- indexer.index("foo", SomeTestClass("alpha", 1).versionedRecord(id = "A", version = 2))
        b <- indexer.index("foo", SomeTestClass("alpha", 2).versionedRecord(id = "B", version = 3))
        c <- indexer.index("bar", MoreTestData("alpha", 2).versionedRecord(id = "A"))
      } yield (a, b, c)

      val (firstInsert, secondInsert, thirdInsert) = test.unsafeRunSync()

      withClue("the first insert should've set three indices for each value") {
        val byValue = firstInsert.map {
          case (value, ref, result) => (value, (ref, result))
        }.toMap.ensuring(_.size == 3)
        byValue("alpha")._1 shouldBe ReferenceToValue("foo", List("name"), "A", 2)
        byValue("1")._1 shouldBe ReferenceToValue("foo", List("number"), "A", 2)
        byValue("true")._1 shouldBe ReferenceToValue("foo", List("truthy"), "A", 2)

        val FixedReferences(references1) = byValue("alpha")._2
        val FixedReferences(references2) = byValue("1")._2
        val FixedReferences(references3) = byValue("true")._2

        references1 should contain only (ReferenceToValue("foo", List("name"), "A", 2))
        references2 should contain only (ReferenceToValue("foo", List("number"), "A", 2))
        references3 should contain only (ReferenceToValue("foo", List("truthy"), "A", 2))
      }

      withClue("the second insert should've set three indices for each value") {
        val byValue = secondInsert.map {
          case (value, ref, result) => (value, (ref, result))
        }.toMap.ensuring(_.size == 3)
        byValue("alpha")._1 shouldBe ReferenceToValue("foo", List("name"), "B", 3)
        byValue("2")._1 shouldBe ReferenceToValue("foo", List("number"), "B", 3)
        byValue("true")._1 shouldBe ReferenceToValue("foo", List("truthy"), "B", 3)

        val FixedReferences(references) = byValue("true")._2
        withClue(references.mkString("\n")) {
          references.size shouldBe 2
        }
      }

      val Some(FixedReferences(alphaValues)) = indexer.read("alpha").unsafeRunSync()
      alphaValues should contain only(
        ReferenceToValue("foo", List("name"), "A", 2),
        ReferenceToValue("foo", List("name"), "B", 3),
        ReferenceToValue("bar", List("text"), "A", 0))
    }
  }
}
