package franz.db.impl

import franz.IntegrationTest
import franz.data.VersionedRecord
import franz.db.BaseGleichDBTest
import io.circe.Json
import mongo4m.LowPriorityMongoImplicits
import monix.execution.CancelableFuture
import monix.execution.Scheduler.Implicits.global
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model.Filters

import scala.concurrent.Await

trait LatestRecordsMongoTest extends BaseGleichDBTest {

  "LatestRecordsMongo.insertLatestRecord" should {

    "fail if attempting to replace the first version" taggedAs(IntegrationTest) in {
      val collName = s"latest${nextId()}"

      val coll: DocCollection = {
        val vc: VersionedRecordsMongo = VersionedRecordsMongo()
        val settings = vc.latestSettingsForName(latestNameFor(collName))
        vc.cache.withCollection(settings)(identity).runToFuture.futureValue
      }

      Given("Some initial version of a record in the database")
      val firstRecord = VersionedRecord(Json.fromString("some record"), userId = "dave", id = s"first${nextId()}")
      val insertIO = LatestRecordsMongo.insertLatestRecord(coll, firstRecord)
      insertIO.runToFuture.futureValue shouldBe None
      LatestRecordsMongoTest.listAllForId(coll, firstRecord.id).futureValue should contain only (firstRecord)

      When("We try and insert a record with the same version")
      val err = intercept[Exception] {
        Await.result(LatestRecordsMongo.insertLatestRecord(coll, firstRecord.copy(data = Json.Null)).runToFuture, testTimeout)
      }
      err.getMessage should include("duplicate key error collection")
    }

    "fail if attempting to replace later versions" taggedAs(IntegrationTest) in {

      val collName = s"latest${nextId()}"

      val coll: DocCollection = {
        val vc = VersionedRecordsMongo()
        val settings = vc.latestSettingsForName(latestNameFor(collName))
        vc.cache.withCollection(settings)(identity).runToFuture.futureValue
      }

      Given("Some initial version of a record in the database")
      val firstRecord = VersionedRecord(Json.fromString("some record"), userId = "dave", id = s"first${nextId()}")
      LatestRecordsMongo.insertLatestRecord(coll, firstRecord).runToFuture.futureValue shouldBe None


      val v2 = firstRecord.incVersion.incVersion
      val Some(updateResult) = LatestRecordsMongo.insertLatestRecord(coll, v2).runToFuture.futureValue
      updateResult.getMatchedCount shouldBe 1

      LatestRecordsMongoTest.listAllForId(coll, firstRecord.id).futureValue should contain only (v2)

      When("We try and insert a record with the same version")
      val err = intercept[Exception] {
        Await.result(LatestRecordsMongo.insertLatestRecord(coll, firstRecord.copy(data = Json.Null)).runToFuture, testTimeout)
      }
      Then("It should fail")
      err.getMessage should include("duplicate key error collection")

      LatestRecordsMongoTest.listAllForId(coll, firstRecord.id).futureValue should contain only (v2)
    }
    "replace earlier versions" taggedAs(IntegrationTest) in {
      val collName = s"earlier${nextId()}"
      val firstRecord = VersionedRecord(Json.fromString("some record"), userId = "dave", id = s"happy${nextId()}")

      val coll: DocCollection = {
        val vc = VersionedRecordsMongo()
        val settings = vc.latestSettingsForName(latestNameFor(collName))
        vc.cache.withCollection(settings)(identity).runToFuture.futureValue
      }

      LatestRecordsMongoTest.listAllForId(coll, firstRecord.id).futureValue should be(empty)

      Given("A new record (new id and version) is inserted into the 'latest' collection")
      LatestRecordsMongo.insertLatestRecord(coll, firstRecord).runToFuture.futureValue shouldBe None

      Then("we should be able to read it back")
      val Seq(firstRecordReadBack) = LatestRecordsMongoTest.listAllForId(coll, firstRecord.id).futureValue
      firstRecordReadBack shouldBe firstRecord

      When("We insert a record w/ the same ID but at a later version")
      val updatedRecord = firstRecord.incVersion
      val Some(secondUpdate) = LatestRecordsMongo.insertLatestRecord(coll, updatedRecord).runToFuture.futureValue
      secondUpdate.getModifiedCount shouldBe 1

      Then("the original, lower-versioned record should be removed")
      val Seq(secondRecordReadBack) = LatestRecordsMongoTest.listAllForId(coll, firstRecord.id).futureValue
      secondRecordReadBack shouldBe updatedRecord
    }
  }
}

object LatestRecordsMongoTest extends LowPriorityMongoImplicits {
  def listAllForId(coll: DocCollection, id: String): CancelableFuture[List[VersionedRecord[Json]]] = {
    coll.find[BsonDocument](Filters.eq(VersionedRecord.fields.Id, id))
      .monix
      .map(docAsVersionedA[Json])
      .map(_.get)
      .toListL
      .runToFuture
  }

}
