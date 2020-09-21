package franz.test

import java.nio.file.Path

import cats.syntax.option._
import com.typesafe.scalalogging.StrictLogging
import franz.app.mongo.MongoServices
import franz.app.{LiveRecorder, MainEntryPoint}
import franz.db.impl.VersionedRecordsMongo
import franz.test.steps.FeatureGenerator
import zio.ZIO
import zio.interop.catz._

/**
 * Another entry-point to our app, but one which goes straight to creating features from session dumps
 */
object DevAppFeature extends CatsApp with StrictLogging {

  import MainEntryPoint.TaskToZio

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = {
    dockerenv.mongo().start()
    val config = MainEntryPoint.argsAsConfig(args)
    val services = MongoServices(config, (recordSession _).some)
    MainEntryPoint(services.map(_._2))
  }

  def recordSession(db: VersionedRecordsMongo) = {
    val recorder = LiveRecorder(db, createFeature)
    (recorder.log _)
  }

  def createFeature(dir: Path, testName: String) = {
    val featureDir = FeatureGenerator.generateFeatureFromDump(dir, testName)
    logger.info(s"Created feature in ${featureDir}")
  }
}
