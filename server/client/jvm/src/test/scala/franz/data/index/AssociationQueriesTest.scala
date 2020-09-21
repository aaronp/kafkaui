package franz.data.index

import cats.effect.IO
import franz.Env
import franz.data.SomeTestClass
import franz.data.VersionedRecord.syntax._
import franz.data.crud.InsertSuccess
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AssociationQueriesTest extends AnyWordSpec with Matchers {

  "IndexRest" should {
    "replace indexed values with their new versions even when the values differ" in {
      val env = Env()
      import env.implicits._
      val indexer = IndexingCrud.inMemory[IO](false)
      val app = indexer.indexingCrud
      val indexReader = indexer.associationQueries.readIndex
      val InsertSuccess(0, _) = app.insert("collection1", SomeTestClass().versionedRecord(id = "ABC")).unsafeRunSync()
      val Some(FixedReferences(nameIndex)) = indexReader.read("anon").unsafeRunSync()
      val Some(FixedReferences(numberIndex)) = indexReader.read("1234").unsafeRunSync()
      nameIndex.toList shouldBe List(ReferenceToValue("collection1", Seq("name"), "ABC", 0))
      numberIndex.toList shouldBe List(ReferenceToValue("collection1", Seq("number"), "ABC", 0))
    }
  }
}
