package franz.data.index

import cats.effect.IO
import franz.Env
import franz.data.QueryRange._
import franz.data.VersionedRecord
import franz.data.VersionedRecord.syntax._
import franz.data.crud.InsertSuccess
import io.circe.generic.auto._
import io.circe.syntax._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class UpdateIndexesTest extends AnyWordSpec with Matchers {

  import UpdateIndexesTest._

  "UpdateIndexes" should {
    "index records with different paths to the same value" in {
      val env = Env()
      import env.implicits._
      val setup: IndexingCrud[IO] = IndexingCrud.inMemory[IO](true, 10)
      val indexer: Indexer.Service[IO] = setup.indicesCrud
      val crud = setup.indexingCrud

      val InsertSuccess(1, _) = crud.insert("ints", LotsOfInts(111).versionedRecord(id = "ABC", version = 1).mapToJson).unsafeRunSync()

      val indexById = {
        val indices = indexer.list(Default).unsafeRunSync()
        indices.map(r => r.id -> r).toMap.ensuring(_.size == indices.size)
      }
      indexById.keySet should contain only ("111")
      indexById("111").version should be > 1
      val values: VersionedRecord[IndexedValue] = indexById("111")
      val FixedReferences(refs) = values.data

      val expected = ("abcdefg".map(_.toString) :+ "hij").map { path =>
        ReferenceToValue("ints", Seq(path), "ABC", 1)
      }
      refs should contain only (expected: _*)
      refs.size shouldBe expected.size
    }

    "remove old indices and update new ones" in {
      val env = Env()
      import env.implicits._
      val setup = IndexingCrud.inMemory[IO](true, 10)
      val indexer = setup.indicesCrud
      val crud = setup.indexingCrud

      val InsertSuccess(1, _) = crud.insert("foo", SomeUser().asJson.versionedRecord(id = "ABC", version = 1)).unsafeRunSync()

      val originalIndicesById: Map[String, VersionedRecord[IndexedValue]] = {
        val originalIndices: Iterable[VersionedRecord[IndexedValue]] = indexer.list(Default).unsafeRunSync()
        originalIndices.map(r => r.id -> r).toMap.ensuring(_.size == originalIndices.size)
      }
      originalIndicesById.keySet should contain only("55", "alpha", "em@il.com")

      val InsertSuccess(2, _) = crud.insert("foo", AdvancedUser().asJson.versionedRecord(id = "ABC", version = 2)).unsafeRunSync()

      val indexById = {
        val indices: Iterable[VersionedRecord[IndexedValue]] = indexer.list(Default).unsafeRunSync()
        indices.map(r => r.id -> r).toMap.ensuring(_.size == indices.size)
      }
      withClue("old version should not be removed") {
        indexById.keySet should contain ("alpha")
      }
      indexById.keySet should contain only("alpha", "em@il.com", "55", "beta", "true", "upd@ted.com")
      withClue("unchanged values should've had their references updated") {
        indexById("55").version shouldBe 2
        val FixedReferences(fiftyFive) = indexById("55").data

        // this field was removed in version 2
        fiftyFive should not contain (ReferenceToValue("foo", List("oldField"), "ABC", 1))

        fiftyFive should contain only (ReferenceToValue("foo", List("age"), "ABC", 2))

        val FixedReferences(beta) = indexById("beta").data
        beta should contain only (ReferenceToValue("foo", List("name"), "ABC", 2))

        val FixedReferences(truish) = indexById("true").data
        truish should contain only (ReferenceToValue("foo", List("newField"), "ABC", 2))

        val FixedReferences(updtedcom) = indexById("upd@ted.com").data
        updtedcom should contain only (ReferenceToValue("foo", List("email"), "ABC", 2))

      }
    }
  }
}

object UpdateIndexesTest {

  case class LotsOfInts(a: Int, b: Int, c: Int, d: Int, e: Int, f: Int, g: Int, hij: Int)

  object LotsOfInts {
    def apply(x: Int) = new LotsOfInts(x, x, x, x, x, x, x, x)
  }

  case class SomeUser(name: String = "alpha", age: Int = 55, email: String = "em@il.com", oldField: Int = 55)

  case class AdvancedUser(name: String = "beta", age: Int = 55, email: String = "upd@ted.com", newField: Boolean = true)

}
