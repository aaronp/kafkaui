package franz.data.index

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.util.Success

class IndexedValueTest extends AnyWordSpec with Matchers {

  val ref1 = ReferenceToValue("collection", Seq("foo", "bar"), "id", 123)
  val sameRecordInDifferentCollection = ReferenceToValue("collection2", Seq("foo", "bar"), "id", 123)
  val differentRecordInSameCollection = ReferenceToValue("collection", Seq("foo", "bar"), "id2", 123)
  val sameIdDifferentPath = ReferenceToValue("collection", Seq("fizz"), "id", 123)
  val references = FixedReferences(Set(ref1, sameRecordInDifferentCollection, differentRecordInSameCollection, sameIdDifferentPath))

  "IndexedValue" should {
    List(
      IndexedValue.tooManyValues(123),
      IndexedValue(ReferenceToValue("a", "b" :: Nil, "c", 1))
    ) foreach { input =>
      s"serialize $input from json" in {
        val in: IndexedValue = input
        import io.circe.syntax._
        in.asJson.as[IndexedValue].toTry shouldBe Success(input)
      }
    }
  }
  "FixedReferences.insertOrReplace" should {
    "add multiple references if they are for different paths but the same collection/id/version" in {
      val ref1 = ReferenceToValue("collection", Seq("fizz"), "id", 123)
      val ref2Old = ReferenceToValue("collection", Seq("buzz"), "id", 122)
      val ref2New = ReferenceToValue("collection", Seq("buzz"), "id", 123)
      val references = FixedReferences(Set(ref1, ref2Old))

      val (FixedReferences(updated), true) = references.insertOrReplace(ref2New, 10)
      updated should contain only(ref1, ref2New)
    }
    "replace old references for earlier versions" in {
      val ref1 = ReferenceToValue("collection", Seq("fizz"), "id", 123)
      val refOld1 = ReferenceToValue("collection", Seq("outdated1"), "id", 122)
      val refOld2 = ReferenceToValue("collection", Seq("outdated2"), "id", 2)
      val ref2New = ReferenceToValue("collection", Seq("buzz"), "id", 123)
      val references = FixedReferences(Set(ref1, refOld1, refOld2))

      val (FixedReferences(updated), true) = references.insertOrReplace(ref2New, 10)
      updated should contain only(ref1, ref2New)
    }
    "return a TooManyValues if the value would take it over the threshold" in {
      references.insertOrReplace(ref1.copy(id = "new id"), 4) shouldBe(TooManyValues(4), false)
      val newRef = ref1.copy(id = "new id")
      val (FixedReferences(updated), false) = references.insertOrReplace(newRef, 5)
      updated should contain(newRef)
    }
    "insert the reference if there are no previous versions" in {
      val newRef = ref1.copy(version = ref1.version + 1)
      val (FixedReferences(updated), true) = references.insertOrReplace(newRef, 10)
      updated should not contain (ref1)

      updated should contain (newRef)
      updated should contain (sameRecordInDifferentCollection)
      updated should contain (differentRecordInSameCollection)
      withClue("this path doesn't appear in the new version, so it should be removed") {
        updated should not contain (sameIdDifferentPath)
      }
      updated should contain only(newRef, sameRecordInDifferentCollection, differentRecordInSameCollection)
    }
    "insert references if there are no previous versions" in {
      val newRef = ref1.copy(collection = "unique")
      val (FixedReferences(updated), false) = references.insertOrReplace(newRef, 10)

      updated should contain only(newRef, ref1, sameRecordInDifferentCollection,
        differentRecordInSameCollection, sameIdDifferentPath)
    }
  }


}
