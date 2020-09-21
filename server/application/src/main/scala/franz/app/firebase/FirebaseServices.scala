package franz.app.firebase

import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import franz.firestore.services.{FirestoreIndexingCrud, UsersFirestore}
import franz.firestore.{FS, FSEnv}
import franz.jwt.PasswordHash
import franz.rest.RestServices
import franz.users.Login
import franz.{AppServicesJVM, GleichApiJVM}
import zio.{Runtime, ZIO}
import zio.interop.catz._
//import zio.interop.catz._

object FirebaseServices extends StrictLogging {

  /**
   * Set up our application components
   *
   * @param config          the typesafe config
   * @return an task which create the RestApp.Input
   */
  def apply(config: Config)(implicit runtime: Runtime[FSEnv]): ZIO[FSEnv, Throwable, RestServices[FS]] = {
    implicit val platform = runtime.platform
    for {
      original: RestServices[FS] <- RestServices.inMemory[FS](config)
      jwtSeed = GleichApiJVM.seedForConfig(config)
      hasher = PasswordHash(config)
      users = UsersFirestore(jwtSeed, hasher)
    } yield {

      val firebaseUserServices = {
        val login: Login.Service[FS] = users.loginService
        val jwtLogin = original.userServices.jwt.wrap(login)
        original.userServices.copy(loginService = jwtLogin, createUserService = users.createUserService)
      }

      val liveIndexing = FirestoreIndexingCrud(AppServicesJVM.tooManyValuesThreshold(config))

      // if we 'record', then we get e.g. "GET /d/foo" to dump out a request/response test
      original.copy(userServices = firebaseUserServices, appServices = liveIndexing.appServices)
    }
  }
}
