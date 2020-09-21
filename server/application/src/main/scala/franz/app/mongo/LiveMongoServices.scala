package franz.app.mongo

import cats.syntax.option._
import cats.~>
import com.typesafe.config.Config
import franz.app.{LiveRecorder, mongo}
import franz.db.impl.VersionedRecordsMongo
import franz.rest.RestServices
import zio.{Runtime, Task, ZEnv}

object LiveMongoServices {

  def apply(config: Config, record: Boolean)(implicit runtime: Runtime[ZEnv], monixInterop: monix.eval.Task ~> zio.Task): Task[(VersionedRecordsMongo, RestServices[Task])] = {
    implicit val pf = runtime.platform
    if (record) {
      def recordSession(db: VersionedRecordsMongo) = {
        val recorder = LiveRecorder(db)
        (recorder.log _)
      }

      MongoServices(config, (recordSession _).some)
    } else {
      mongo.MongoServices(config)
    }
  }
}
