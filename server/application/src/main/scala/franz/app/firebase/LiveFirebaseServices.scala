package franz.app.firebase

import cats.arrow.FunctionK
import com.typesafe.config.Config
import franz.firestore.{FS, FSEnv}
import franz.rest.{RestServices, UserServices}
import zio.internal.Platform
import zio.interop.catz._
import zio.{Runtime, Task, ZIO, ZLayer}

object LiveFirebaseServices {

  def apply(config: Config, live: ZLayer[Any, Throwable, FSEnv] = FSEnv.live)(implicit platform: Platform): ZIO[Any, Throwable, RestServices[Task]] = {
    val nested: ZIO[Any, Throwable, ZIO[Any, Throwable, RestServices[Task]]] = live.toRuntime(platform).use { implicit fsEnvRuntime: Runtime[FSEnv] =>
      val env: FSEnv = fsEnvRuntime.environment
      implicit object FSToAny extends FunctionK[FS, Task] {
        override def apply[A](fa: FS[A]): Task[A] = fa.provide(env)
      }
      FirebaseServices(config).provide(env).map { servicesFS: RestServices[FS] =>

        // yuck
        zio.interop.catz.TaskConcurrentEffectOps(Task).concurrentEffect.flatMap { implicit zioConcurrentEffect =>

          // we still use our noddy admin api
          UserServices.inMemory[Task](config).map {
            case (_, adminApi) =>
              RestServices[Task](
                httpSettings = servicesFS.httpSettings.mapK[Task],
                userServices = servicesFS.userServices.mapK[Task],
                adminServices = adminApi,
                appServices = servicesFS.appServices.mapK[Task]
              )
          }
        }
      }
    }
    nested.flatten
  }
}
