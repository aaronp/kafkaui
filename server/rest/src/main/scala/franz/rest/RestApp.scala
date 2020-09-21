package franz.rest

import cats.effect.{ConcurrentEffect, ContextShift, ExitCode, Sync, Timer}
import cats.implicits._
import cats.{Functor, Parallel}
import com.typesafe.config.Config
import franz.UserApi
import franz.ui.routes.StaticFileRoutes
import franz.users.routes.{CreateUserRoute, _}
import franz.users.{Login, _}
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import org.http4s.{AuthedRoutes, HttpApp, HttpRoutes}

import scala.concurrent.ExecutionContext

case class RestApp[F[_]](services: RestServices[F], app: HttpApp[F])

/**
 * The REST service application - creates routes for services
 */
object RestApp {

  /**
   * The main application code in the effect F
   *
   * @param config the configuration
   * @tparam F the effect type (IO, ZIO, whatever)
   * @return the return code
   */
  def runF[F[_] : ConcurrentEffect : Timer : ContextShift : Parallel](config: Config): F[ExitCode] = {
    for {
      input <- RestServices.inMemory[F](config)
      exit <- runF[F](input)
    } yield exit
  }

  /**
   * Runs the REST Application with these services
   *
   * @param input
   * @tparam F
   * @return the result of the web app
   */
  def runF[F[_] : ConcurrentEffect : Timer : ContextShift : Parallel](input: RestServices[F],
                                                                      additionalRoutes: (String, HttpRoutes[F])*): F[ExitCode] = {
    val restApp = forServices[F](input, additionalRoutes :_*)

    val server = BlazeServerBuilder[F](ExecutionContext.Implicits.global).bindHttp(input.port, input.host).withHttpApp(restApp.app).serve
    server.compile.drain.as(ExitCode.Success)
  }

  /**
   * @param config
   * @tparam F
   * @return
   */
  def apply[F[_] : ConcurrentEffect : ContextShift : Parallel](config: Config): F[RestApp[F]] = {
    Functor[F].map(RestServices.inMemory(config))(svc => forServices[F](svc))
  }

  /**
   * RestApp.forServices ...
   *
   * this bootstraps the services based on the configuration
   *
   * @param services the input configuration
   * @tparam F
   * @return the RestApp services
   */
  def forServices[F[_] : ConcurrentEffect : ContextShift : Parallel](services: RestServices[F],
                                                                     additionalRoutes: (String, HttpRoutes[F])*): RestApp[F] = {
    val routes: AuthedRoutes[(JWT, User), F] = RestRoutes(services)
    val app = makeApp(services, routes, additionalRoutes :_*)
    new RestApp[F](services, app)
  }

  /**
   * @return the HttpApp
   */
  def makeApp[F[_] : ConcurrentEffect : ContextShift](services: RestServices[F],
                                                      underlyingAuthedRoutes: AuthedRoutes[(JWT, User), F],
                                                      additionalRoutes: (String, HttpRoutes[F])*): HttpApp[F] = {

    //
    // refresh the JWT session duration on each successful, authed request
    //
    val appRoutes = {
      Auth.withRefreshedTokens(
        underlyingAuthedRoutes, // the routes we want to convert into Request[F] => Response[F]
        services.userServices.jwt, // the service for setting/invalidating tokens
        services.adminServices.userRoles, // means to determine a particular user's roles
        services.userServices.jwtSeed, // needed to create the JWT string for a User
        services.httpSettings.sessionDuration // needed to extend a token's expiry time
      )
    }

    val unauthed = {
      // ui routes don't require auth - nor does creating new users
      unauthedRoutes(services.userServices.loginService, services.userServices.jwt, services.httpSettings.uiRoutes) <+> //
        CreateUserRoute(services.userServices.createUserService)
    }


    val httpApp = {
      val allRoutes = {
        ("/" -> unauthed) :: ("/rest" -> appRoutes) :: additionalRoutes.toList
      }
      Router(allRoutes: _*).orNotFound
    }

    import services.httpSettings._
    Logger.httpApp(logHeaders, logBody, redactHeadersWhen, logAction)(httpApp)
  }

  /**
   * @param appServices the services which will drive the routes
   * @tparam F the effect type F
   * @return the routes which require an authorized user
   */
  def authedRoutes[F[_] : Sync](appServices: UserApi[F],
                                adminServices: AdminApi[F]
                               ): AuthedRoutes[WebUser, F] = {
    import cats.implicits._
    UserHealthRoute[F] <+> // a basic echo route for health
      UserRolesRoute[F](adminServices.rolesService, adminServices) <+> // our user/roles route
      RolesRoute[F](adminServices.rolesService) // our user auth/roles. We may want to turn this off via config
  }

  def unauthedRoutes[F[_] : Sync : ContextShift](service: Login.Service[F], jwt: JWTCache[F], uiRoutes: StaticFileRoutes) = {
    LoginRoute(service, jwt) <+> uiRoutes.routes[F]()
  }
}
