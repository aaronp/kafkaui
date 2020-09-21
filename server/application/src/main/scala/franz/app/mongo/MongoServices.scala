package franz.app.mongo

import cats.~>
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import franz.AppServicesJVM
import franz.db.impl.VersionedRecordsMongo
import franz.db.services.{MongoIndexingCrud, UsersMongo}
import franz.rest.{RestServices, UserServices}
import zio._
import zio.interop.catz._
//import zio.interop.catz._
import cats.syntax.option._

/**
 * Our live, production values for the application's services
 */
object MongoServices extends StrictLogging {

  type LogAction = String => Task[Unit]

  /**
   * Set up our application components
   *
   * @param config          the typesafe config
   * @param makeRecorderOpt an optional function which can create a logger
   * @return an task which create the RestApp.Input
   */
  def apply(config: Config, makeRecorderOpt: Option[VersionedRecordsMongo => LogAction] = None)(implicit runtime: Runtime[ZEnv], monixInterop: monix.eval.Task ~> zio.Task) = {
    for {
      original: RestServices[Task] <- RestServices.inMemory[zio.Task](config)
      UsersMongo.Init(versionedRecordsMongo, _, hasher) = UsersMongo.Init(config)
      mongoUsers = UsersMongo(versionedRecordsMongo, original.userServices.jwtSeed, hasher).asUserApi.mapK[Task]
    } yield {

      val mongoUserServices: UserServices[Task] = {
        val login = mongoUsers.loginService
        val jwtLogin = original.userServices.jwt.wrap(login)
        original.userServices.copy(loginService = jwtLogin, createUserService = mongoUsers.createUserService)
      }

      val liveIndexing = MongoIndexingCrud(versionedRecordsMongo, AppServicesJVM.tooManyValuesThreshold(config))

      // if we 'record', then we get e.g. "GET /d/foo" to dump out a request/response test
      val newHttpSettings = makeRecorderOpt.map(_ (versionedRecordsMongo)) match {
        case Some(recorder) =>
          original.httpSettings.copy(logAction = recorder.some, logBody = true, logHeaders = true)
        case None => original.httpSettings
      }
      val liveServices = original.copy(httpSettings = newHttpSettings, userServices = mongoUserServices, appServices = liveIndexing.mongoAppServices.mapK[Task])
      (versionedRecordsMongo, liveServices)
    }
  }
}
