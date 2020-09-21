package franz.rest

import cats.Parallel
import cats.effect.{ConcurrentEffect, ContextShift}
import cats.syntax.functor._
import com.typesafe.config.{Config, ConfigFactory}
import franz.users._
import franz.{AppServices, AppServicesJVM}

/**
 * @param httpSettings  the REST settings
 * @param userServices  the services needed for user functions
 * @param adminServices the services needed for admin functions
 * @param appServices   our application services
 * @tparam F the effect type
 */
case class RestServices[F[_]](httpSettings: HttpSettings[F],
                              userServices: UserServices[F],
                              adminServices: AdminApi[F],
                              appServices: AppServices[F]) {
  def permissions: PermissionPredicate[F] = userServices.permissions

  def port = httpSettings.hostPort._2

  def host = httpSettings.hostPort._1
}

object RestServices {

  /**
   * Bootstrap the services based on the configuration
   *
   * @param config the input configuration
   * @tparam F
   * @return the RestApp services
   */
  def inMemory[F[_] : ConcurrentEffect : ContextShift : Parallel](config: Config = ConfigFactory.load()): F[RestServices[F]] = {
    for {
      (userServices, admin) <- UserServices.inMemory[F](config)
      app = AppServicesJVM.inMemory[F](config)
      httpSettings = HttpSettings[F](config)
    } yield RestServices[F](httpSettings, userServices, admin, app)
  }
}
