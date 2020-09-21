package franz.test.steps

import com.typesafe.config.{Config, ConfigFactory}
import franz.app.{MainEntryPoint, MonixToZIO}
import franz.app.mongo.LiveMongoServices
import franz.db.GleichDB
import franz.db.impl.VersionedRecordsMongo
import franz.rest.RestServices
import franz.test.steps.TestState.Running
import monix.execution.Scheduler
import monix.execution.Scheduler.Implicits.global
import zio.internal.Platform
import zio.{Task, ZIO}

import scala.concurrent.{Future, Promise}
import scala.util.Success

private[steps] object StartService extends IntegrationAssertions {

  /**
   * Starts up the service using the database found in the given path (which may be empty)
   *
   * @param dbStatePath a directory holding data_*.json files from a [[franz.app.LiveRecorder]] session
   * @return a running web service
   */
  def apply(dbStatePath: String): Future[Running] = {
    val dbConfig: Config = {
      val fallback = sys.env.get("TEST_CONFIG") match {
        case Some(custom) =>
          // the IntelliJ run config specifies this as the paths are different if running from IntelliJ and SBT.
          // the SBT one will pick up application.conf, but IJ should be set to use cucumbertest-intellij.conf
          import args4c.implicits._
          val fb = Array(custom).asConfig().resolve()

          fb
        case None => ConfigFactory.load()
      }

      GleichDB.withDatabase(s"TEST${System.currentTimeMillis()}", fallback)
    }

    val snapshots = SnapshotsForDir(dbStatePath)

    // yuck - please don't judge me
    val dbPromise = Promise[VersionedRecordsMongo]()
    val appTask: ZIO[Any, Nothing, Int] = {
      implicit val runtime = zio.Runtime.default
      implicit val TaskToZio: MonixToZIO = MonixToZIO(Scheduler.io("monix-app-io"))
      val services = LiveMongoServices(dbConfig, false).map {
        case (db: VersionedRecordsMongo, services) =>
          dbPromise.tryComplete(Success(db))
          snapshots.foreach { snapshot =>
            logger.info(s"Backing up from ${snapshot.collectionName}")
            db.snapshots.backupFromSnapshot(snapshot).runToFuture.futureValue
          }
          services
      }
      MainEntryPoint(services)
    }

    val promise = Promise[Int]()

    franz.app.MainEntryPoint.unsafeRunAsync(appTask) { exitCode =>
      val either = exitCode.toEither
      promise.tryComplete(either.toTry)
    }
    dbPromise.future.map(db => Running(db, promise.future))
  }
}
