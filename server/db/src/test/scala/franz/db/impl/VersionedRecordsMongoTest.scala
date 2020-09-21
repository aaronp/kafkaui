package franz.db.impl

import franz.IntegrationTest
import franz.data.VersionedRecord
import franz.db.BaseGleichDBTest
import franz.db.impl.VersionedRecordsMongoSnapshot.{LatestSnapshot, RecordVersions, VersionSnapshot}
import io.circe.Json
import io.circe.literal._
import monix.execution.Scheduler.Implicits.global
import org.mongodb.scala.MongoWriteException

import scala.concurrent.Await

trait VersionedRecordsMongoTest extends BaseGleichDBTest {

  "VersionedInsert.insertIntoCollection" should {

    "update the base collection with the latest version" taggedAs (IntegrationTest) in {
      val versionedInsert: VersionedRecordsMongo = VersionedRecordsMongo()

      val record = VersionedRecord(Json.fromString("some record"), userId = "dave")
      val collectionName = nextCollectionName()

      Given("An initial insert of a record")
      versionedInsert.write.insertIntoCollection(collectionName, record).runToFuture.futureValue

      Then("We should see the entry in the 'versioned' and 'latest' collections")
      val Some(latest) = versionedInsert.cache.collectionForName(latestNameFor(collectionName))
      val Some(versioned) = versionedInsert.cache.collectionForName(versionedNameFor(collectionName))

      val Seq(_) = LatestRecordsMongoTest.listAllForId(versioned, record.id).futureValue
      val Seq(_) = LatestRecordsMongoTest.listAllForId(latest, record.id).futureValue

      When("We insert the next version")
      versionedInsert.write.insertIntoCollection(collectionName, record.incVersion).runToFuture.futureValue

      Then("We should see both versioned entries and only the v1 record in the latest")
      LatestRecordsMongoTest.listAllForId(versioned, record.id).futureValue.size shouldBe 2

      val Seq(v1) = LatestRecordsMongoTest.listAllForId(latest, record.id).futureValue
      v1 shouldBe record.incVersion

      When("We insert a record with a different Id")
      val different = record.copy(id = s"different${nextId()}")
      versionedInsert.write.insertIntoCollection(collectionName, different).runToFuture.futureValue


      Then("We should see only that version of the different record")
      LatestRecordsMongoTest.listAllForId(versioned, different.id).futureValue.size shouldBe 1

      val Seq(differentReadBack) = LatestRecordsMongoTest.listAllForId(latest, different.id).futureValue
      differentReadBack shouldBe different
    }
    "NOT update the latest collection if we inserting an earlier version" taggedAs (IntegrationTest) in {
      val versionedInsert: VersionedRecordsMongo = VersionedRecordsMongo()

      val record = VersionedRecord(Json.fromString("some record"), userId = "susan")
      val collectionName = nextCollectionName()

      Given("An initial insert of a record")
      versionedInsert.write.insertIntoCollection(collectionName, record).runToFuture.futureValue


      When("We insert a skipped version (v2)")
      val Some(v2) = versionedInsert.write.insertIntoCollection(collectionName, record.incVersion.incVersion).runToFuture.futureValue
      v2.getMatchedCount shouldBe 1

      And("We insert the skipped version (v1)")
      val Some(_) = versionedInsert.write.insertIntoCollection(collectionName, record.incVersion).runToFuture.futureValue

      Then("The 'latest' collection should contain our v2")
      val Some(latest) = versionedInsert.cache.collectionForName(latestNameFor(collectionName))
      LatestRecordsMongoTest.listAllForId(latest, record.id).futureValue should contain only (record.incVersion.incVersion)

      And("The 'versions' collection should contain all three versions")
      val Some(versioned) = versionedInsert.cache.collectionForName(versionedNameFor(collectionName))
      LatestRecordsMongoTest.listAllForId(versioned, record.id).futureValue should contain allOf(record, record.incVersion, record.incVersion.incVersion)
    }

    "fail if we try and insert a document with the same id and version" taggedAs (IntegrationTest) in {

      val versionedInsert: VersionedRecordsMongo = VersionedRecordsMongo()

      val record = VersionedRecord(Json.fromString("some record"), userId = "dave")
      val collectionName = nextCollectionName()

      versionedInsert.write.insertIntoCollection(collectionName, record).runToFuture.futureValue

      intercept[MongoWriteException] {
        Await.result(versionedInsert.write.insertIntoCollection(collectionName, record).runToFuture, testTimeout)
      }

      val List(latestSnapshot: VersionedRecordsMongoSnapshot.LatestSnapshot) = versionedInsert.snapshots.latest(collectionName, false).toListL.runToFuture.futureValue
      val List(versionSnapshot) = versionedInsert.snapshots.versions(collectionName, false).toListL.runToFuture.futureValue

      latestSnapshot.orderedIds shouldBe LatestSnapshot(Map(
        "0" -> json""""some record""""))

      versionSnapshot.orderedIds shouldBe VersionSnapshot(List(
        RecordVersions("id0", List((0, json""""some record"""")))))
    }

    "succeed if we try and insert a document with the version but different id" taggedAs (IntegrationTest) in {
      val versionedInsert = VersionedRecordsMongo()
      val v0 = VersionedRecord(Json.fromString("some record"), userId = "dave")
      val v1 = v0.incVersion

      val collectionName = nextCollectionName()

      versionedInsert.write.insertIntoCollection(collectionName, v0).runToFuture.futureValue shouldBe None
      val Some(v1Result) = versionedInsert.write.insertIntoCollection(collectionName, v1).runToFuture.futureValue
      v1Result.getMatchedCount shouldBe 1


      val List(latestSnapshot: VersionedRecordsMongoSnapshot.LatestSnapshot) = versionedInsert.snapshots.latest(collectionName, false).toListL.runToFuture.futureValue
      val List(versionSnapshot) = versionedInsert.snapshots.versions(collectionName, false).toListL.runToFuture.futureValue
      latestSnapshot.orderedIds shouldBe LatestSnapshot(Map(
        "0" -> json""""some record""""))
      versionSnapshot.orderedIds shouldBe VersionSnapshot(List(
        RecordVersions("id0", List((1, json""""some record""""), (0, json""""some record"""")))))

      val Some(latest) = versionedInsert.latest[Json](collectionName).first(v0.id).runToFuture.futureValue
      latest shouldBe v1
    }
  }
}
