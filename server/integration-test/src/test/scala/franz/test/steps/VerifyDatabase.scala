package franz.test.steps

import franz.data.diff.RecordDiff
import franz.db.impl.VersionedRecordsMongo
import franz.db.impl.VersionedRecordsMongoSnapshot.CollectionSnapshot
import io.circe.syntax._
import monix.execution.Scheduler.Implicits.global

import scala.collection.immutable.ArraySeq

object VerifyDatabase extends IntegrationAssertions {
  private def grouped(snapshots: Seq[CollectionSnapshot]) = {
    snapshots.groupBy(_.collectionName).view.mapValues(_.ensuring(_.size == 1).head).toMap
  }

  def apply(db: VersionedRecordsMongo, dbStatePath: String): Unit = {

    val expectedSnapshots = grouped(ArraySeq.unsafeWrapArray(SnapshotsForDir(dbStatePath)))
    val actualSnapshots = grouped(db.snapshots(verbose = true).toListL.runToFuture.futureValue)

    expectedSnapshots.keySet shouldBe actualSnapshots.keySet

    actualSnapshots.keySet.foreach { collection =>
      withClue(collection) {
        val expected: CollectionSnapshot = expectedSnapshots(collection)
        val actual = actualSnapshots(collection)

        // the only diffs should be timestamps and the actual IDs
        val diff: Seq[RecordDiff] = RecordDiff(expected, actual)

        val IgnoredFields = Set("id", "created", "createdEpochMillis")

        def filterOut(jsonPath: Seq[String]) = {
          IgnoredFields.exists(field => jsonPath.lastOption.exists(_ == field))
        }

        val filteredDiff: Seq[RecordDiff] = diff.filterNot {
          case RecordDiff(path, _, _) => filterOut(path)
        }

        withClue(
          s"""==================================== DB Snapshot ====================================
             |$actual
             |
             |>>>>>>> Whose json is
             |${actual.asJson}
             |
             |====================== Compared to the expected, saved snapshot ======================
             |$expected
             |
             |>>>>>>> Whose json is
             |${expected.asJson}
             |
             |==================== Should be the same, but the unfiltered diff is ==================
             |${diff.mkString("\n")}
             |
             |Filtered is:
             |${filteredDiff.mkString("\n")}
             |
             |""".stripMargin) {
          filteredDiff shouldBe empty
        }
      }
    }
  }
}
