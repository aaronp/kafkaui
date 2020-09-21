package franz.db

import franz.IntegrationTest
import franz.data.VersionedRecord.syntax._
import franz.data.crud.{InsertRecord, InsertRecordAssertions}
import franz.data.{RecordCoords, VersionedJson, VersionedJsonResponse}
import franz.db.impl.VersionedRecordsMongo
import io.circe.syntax._
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global

trait CrudServicesAnyCollectionMongoTest extends BaseGleichDBTest with InsertRecordAssertions {

  "CrudServicesAnyCollectionMongo" should {
    "insert records" taggedAs (IntegrationTest) in {
      val testInsertCollection = nextCollectionName()

      // we always write to the same collection in this test
      val underTest = VersionedRecordsMongo().write.insertService.contractMapInsert[VersionedJson](testInsertCollection -> _)

      verifyInsert[Task, InsertRecord.Service[Task, VersionedJson, VersionedJsonResponse]](underTest)
    }
    "read back records" taggedAs (IntegrationTest) in {
      val testInsertCollection = nextCollectionName()

      val records = VersionedRecordsMongo()

      val written = (0 to 5).map { i =>
        records.write.insertService.insert(testInsertCollection -> i.asJson.versionedRecord(id = "a", version = i)).runToFuture.futureValue
      }
      written.size shouldBe 6

      def readBack(coords: RecordCoords) = {
        val Some(found) = records.read.get[Int](coords).runToFuture.futureValue
        found.version shouldBe found.data
        found.version
      }

      readBack(RecordCoords.latest(testInsertCollection, "a")) shouldBe 5
      readBack(RecordCoords.previous(testInsertCollection, "a", 10)) shouldBe 5
      readBack(RecordCoords.previous(testInsertCollection, "a", 5)) shouldBe 4
      readBack(RecordCoords.previous(testInsertCollection, "a", 4)) shouldBe 3
      readBack(RecordCoords(testInsertCollection, "a", 4)) shouldBe 4
      readBack(RecordCoords(testInsertCollection, "a", 3)) shouldBe 3
      readBack(RecordCoords.next(testInsertCollection, "a", 0)) shouldBe 1
      readBack(RecordCoords.next(testInsertCollection, "a", 1)) shouldBe 2
      readBack(RecordCoords.next(testInsertCollection, "a", 2)) shouldBe 3
    }
  }
}
