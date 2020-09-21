package franz.app

import java.nio.file.Path

import com.typesafe.scalalogging.StrictLogging
import franz.db.impl.VersionedRecordsMongo
import franz.rest.Recorder
import monix.execution.CancelableFuture
import monix.execution.Scheduler.Implicits.global

import scala.util.{Failure, Success}

/**
 * An HTTP Recorder which dumps the database state on startup and links each to a previous state
 */
object LiveRecorder extends StrictLogging {
  def apply(versionedRecordsMongo: VersionedRecordsMongo, onDumpSession: (Path, String) => Unit = (_, _) => ()): Recorder.Buffer = {
    import eie.io._

    val sessionId = System.currentTimeMillis()

    def snapshotDatabase(intoDir: Path): CancelableFuture[Unit] = {
      import monix.execution.Scheduler.Implicits.global
      versionedRecordsMongo.snapshots(verbose = true).foreach { snapshot =>
        import io.circe.syntax._
        intoDir.resolve(s"data_${snapshot.collectionName}.json").text = snapshot.asJson.noSpaces
      }
    }

    var previousDBStatePath: Path = {
      val firstDataDir = Recorder.savedSessionDir.resolve(s"$sessionId-startup").mkDirs()
      snapshotDatabase(firstDataDir)
      firstDataDir
    }

    Recorder(sessionId) {
      case (dir, testName) =>
        dir.resolve("previous-state.data").text = previousDBStatePath.toAbsolutePath.toString

        snapshotDatabase(dir).onComplete {
          case Success(_) =>
            previousDBStatePath = dir
            onDumpSession(dir, testName)
          case Failure(err) =>
            logger.error(s"Snapshot failed: $err")
        }
    }
  }
}
