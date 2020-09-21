package franz.test.steps

import eie.io._
import franz.db.impl.VersionedRecordsMongo
import franz.db.impl.VersionedRecordsMongoSnapshot.CollectionSnapshot
import io.circe.parser.decode
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global

import scala.util.control.NonFatal

/**
 * Code for getting/setting database states
 */
private[steps] object SnapshotsForDir extends IntegrationAssertions {

  def apply(dbStatePath: String): Array[CollectionSnapshot] = {
    val dataDir = FeatureGenerator.featuresDir.resolve(dbStatePath)

    dataDir.children.map { snapshotFile =>
      try {
        decode[CollectionSnapshot](snapshotFile.text).toTry.get
      } catch {
        case NonFatal(err) =>
          fail(s"Couldn't load $dbStatePath, parse error for $snapshotFile: $err")
      }
    }
  }

  /**
   * Replace the current database with the new state
   *
   * @param db
   * @param dbStatePath
   */
  def swapOutDatabase(db: VersionedRecordsMongo, dbStatePath: String): Unit = {
    val snapshots: Array[CollectionSnapshot] = SnapshotsForDir(dbStatePath)
    swapOutDatabase(db, snapshots)
  }

  def swapOutDatabase(db: VersionedRecordsMongo, snapshots: Seq[CollectionSnapshot]): Unit = {
    val dropTask: Task[Unit] = db.read.listCollections().toListL.flatMap { allCollections =>
      logger.info(s"Dropping ${allCollections.size} collections: ${allCollections.mkString(",")}")
      db.cache.dropAll(allCollections)
    }

    val backupTasks = snapshots.map { snapshot =>
      val logIt = Task(logger.info(s"Backing up from ${snapshot.collectionName}"))
      logIt *> db.snapshots.backupFromSnapshot(snapshot).void
    }

    Task.sequence(dropTask :: backupTasks.toList).runToFuture.futureValue
  }

}
