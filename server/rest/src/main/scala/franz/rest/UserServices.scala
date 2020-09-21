package franz.rest

import cats.{Functor, Parallel, ~>}
import cats.effect.{ConcurrentEffect, ContextShift}
import cats.implicits._
import com.typesafe.config.Config
import franz.users._
import franz.{GleichApiJVM, UserApi}
import javax.crypto.spec.SecretKeySpec


case class UserServices[F[_]](override val loginService: Login.Service[F],
                              override val createUserService: CreateUser.Service[F],
                              jwtSeed: SecretKeySpec,
                              jwt: JWTCache.Service[F],
                              permissions: PermissionPredicate[F]) extends UserApi[F] { self =>
  def mapK[G[_]](implicit ev : F ~> G, fncF : Functor[F]) : UserServices[G] = {
    UserServices[G](
      loginService = super.mapK[G].loginService,
      createUserService = createUserService.mapCreateUserK[G],
      jwtSeed,
      jwt.mapK[G],
      permissions.mapK[G]
    )
  }
}

/**
 * Services for dealing with users
 */
object UserServices {

  /**
   * Bootstrap the services based on the configuration
   *
   * @param config the input configuration
   * @tparam F
   * @return the RestApp services
   */
  def inMemory[F[_] : ConcurrentEffect : ContextShift : Parallel](config: Config): F[(UserServices[F], AdminApi[F])] = {
    val seed: SecretKeySpec = GleichApiJVM.seedForConfig(config)

    // TODO - don't do this
    val permissionsOverride = Option(PermissionPredicate.permitAll[F])
    for {
      jwt: JWTCache[F] <- JWTCache.empty[F]
      userServices <- CreateUserInMemory.empty[F](jwt, seed)

      // TODO - we couple the admin routes together with our main app routes -
      // these should be separate things
      rolesInstance <- Roles.empty[F]
      userRolesInstance <- UserRoles.empty[F]
    } yield {
      val permissions: PermissionPredicate[F] = permissionsOverride.getOrElse(rolesInstance)
      val users = new UserServices[F](userServices.loginService, userServices.createUserService, seed, jwt.jwtCache, permissions)
      val admin = AdminApi[F](
        userServices.loginService,
        rolesInstance,
        userRolesInstance
      )
      (users, admin)
    }
  }
}
