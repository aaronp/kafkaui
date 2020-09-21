package franz.db.impl

import franz.IntegrationTest
import franz.data.VersionedRecord.syntax._
import franz.db.BaseGleichDBTest
import franz.db.GleichDB.withDatabase
import franz.db.impl.VersionedRecordsMongoSnapshot._
import io.circe.literal._
import monix.execution.Scheduler.Implicits.global

trait VersionedRecordsMongoSnapshotTest extends BaseGleichDBTest {

  "VersionedRecordsMongoSnapshot" should {
    "be able to restore one database from another's snapshot" taggedAs (IntegrationTest) in {
      val originalDBName = nextDb()
      val cfg = withDatabase(originalDBName)
      val db1 = VersionedRecordsMongo(cfg)

      val record1 = json"""{ "a" : 1, "b" : "two" }""".versionedRecord(id = "alpha")
      val record2 = json"""{ "another" : true }""".versionedRecord(id = "beta")
      db1.write.insert(record1).runToFuture.futureValue
      db1.write.insert(record2).runToFuture.futureValue

      val db1Snap: List[CollectionSnapshot] = db1.snapshots(true).toListL.runToFuture.futureValue
      val db2 = VersionedRecordsMongo(withDatabase(originalDBName + "BACKUP"))
      val written = db1Snap.map { snap =>
        db2.snapshots.backupFromSnapshot(snap).runToFuture.futureValue.toInt
      }
      written.sum should be > 0

      withClue("both databases should have the same collections") {
        val db1Collections = db1.read.listCollections().toListL.runToFuture.futureValue
        val db2Collections = db2.read.listCollections().toListL.runToFuture.futureValue
        db1Collections should contain theSameElementsAs (db2Collections)
      }

      val db2Snapshot = db2.snapshots(true).toListL.runToFuture.futureValue
      db1Snap should contain theSameElementsAs (db2Snapshot)
    }
  }

}
