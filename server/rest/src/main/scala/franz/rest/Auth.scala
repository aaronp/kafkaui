package franz.rest

import cats.data.{Kleisli, OptionT}
import cats.{Applicative, Defer, Functor, Monad}
import com.typesafe.scalalogging.LazyLogging
import franz.users._
import franz.users.routes.{AuthLogic, AuthProvider, JsonWebToken}
import javax.crypto.spec.SecretKeySpec
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Authorization
import org.http4s.server.AuthMiddleware
import org.http4s.util.CaseInsensitiveString
import org.http4s.{AuthedRoutes, _}

import scala.concurrent.duration.FiniteDuration

/**
 * The authentication logic and functions
 */
private[rest] object Auth extends LazyLogging {

  /**
   * Converts the given 'authedRoutes' into a normal Request[F] => Response[F] route by using the supplied services as a means
   * to retrieve (and refresh) the users.
   *
   * The authed routes will have the auth tokens in their responses replaced with an updated/refreshed token
   *
   * @param authedRoutes the routes we want to convert to a  Request[F] => Response[F]
   * @param cache        the JWT cache - a means of looking up, invalidating and setting users
   * @param userRoles    the means to find
   * @param seed
   * @param sessionDuration
   * @tparam F
   * @return
   */
  def withRefreshedTokens[F[_] : Defer : Monad](authedRoutes: AuthedRoutes[WebUser, F],
                                                cache: JWTCache.Service[F],
                                                userRoles: UserRoles.Service[F],
                                                seed: SecretKeySpec,
                                                sessionDuration: FiniteDuration) = {

    val refreshFunction = refreshUser(
      cache,
      sessionDuration,
      userRoles,
      seed
    )

    // wrap the authedRoutes, replacing old tokens/users with the refreshed user
    val refreshedRoutes = refreshToken(authedRoutes, cache, refreshFunction)

    // represent the AuthedRoutes as a normal Request[F] => Response[F] route
    authedAsNormalRoutes(refreshedRoutes, cache)
  }

  /**
   * A means to transform a route which requires our 'WebUser' into a normal Request->Response route by linking
   * in a means to provide the WebUser
   *
   * @param authedRoutes
   * @param cache
   * @tparam F
   * @return
   */
  def authedAsNormalRoutes[F[_] : Monad](authedRoutes: AuthedRoutes[WebUser, F], cache: JWTCache.Service[F]) = {
    wrap(authedRoutes) {
      case Some(Authorization(Credentials.Token(_, jwt))) =>
        Functor[F].map(cache.lookup(jwt)) {
          case Some(user) => Right(user)
          case None => Left(s"No user found for bearer token")
        }
      case Some(Authorization(other)) => Monad[F].pure(Left(s"Required bearer token, but got schema '${other.authScheme}''"))
      case None => Monad[F].pure(Left("Missing bearer token"))
    }
  }

  /**
   * Create a function which will update the user with their most recent roles and refresh the expiry
   *
   * @param cache           the JWT cache
   * @param sessionDuration how long the user's session should be
   * @param userRoles       the user roles service
   * @param seed            the secret used to create the JWT
   * @tparam F the effect type
   * @return a function to update a given user
   */
  def refreshUser[F[_] : Monad](cache: JWTCache.Service[F],
                                sessionDuration: FiniteDuration,
                                userRoles: UserRoles.Service[F],
                                seed: SecretKeySpec): WebUser => F[WebUser] = {
    import franz.users.RichClaims._
    val fa: WebUser => F[WebUser] = (webUser: WebUser) => {
      val (originalJWT: JWT, originalUser: User) = webUser
      originalUser.refresh(originalJWT, cache, userRoles, sessionDuration, seed)
    }

    fa
  }

  /**
   * maps the handler to ensure the JWT token is refreshed in the response (e.g. extends the user's session).
   *
   * If the inner route returns a response with the same bearer token as what the request had, then this function replaces that
   * original token with a newly generated one
   *
   * @param authedRoutes the inner route logic we're wrapping
   * @param cache
   * @param updateUser   the means to refresh the user
   * @tparam F
   * @tparam A
   * @return
   */
  def refreshToken[F[_] : Defer : Monad, A](authedRoutes: AuthedRoutes[WebUser, F],
                                            cache: JWTCache.Service[F],
                                            updateUser: WebUser => F[WebUser]): AuthedRoutes[WebUser, F] = {
    AuthedRoutes[WebUser, F] { request: AuthedRequest[F, (JsonWebToken, Claims)] =>
      val (originalJWT: JsonWebToken, originalUser) = request.context

      // we could look at kicking this off in parallel to our token refresh job
      val resp: OptionT[F, Response[F]] = authedRoutes(request)

      // this 'F' is likely IO (or ZIO), so will be lazy here).
      val refreshF = updateUser(originalJWT, originalUser)

      resp.semiflatMap { response =>

        // only replace the AUTH header if we're using the old one
        val existingAuth: Option[Authorization] = response.headers.get(headers.Authorization)

        existingAuth.fold(Applicative[F].point(response)) { responseHeader =>
          responseHeader.credentials match {
            case Credentials.Token(AuthScheme.Bearer, `originalJWT`) =>
              import cats.syntax.functor._
              refreshF.map {
                case (token, _) =>
                  logger.trace(s"""Replacing $originalJWT with $token""".stripMargin)
                  val authHeader = Authorization(Credentials.Token(AuthScheme.Bearer, token))
                  response.withHeaders(authHeader)
              }
            case Credentials.Token(AuthScheme.Bearer, other) =>
              logger.debug(s"""NOT replacing bearer token $other as it looks to already have been altered""".stripMargin)
              Applicative[F].point(response)
            case other =>
              logger.debug(s"""NOT replacing other auth token '$other'""".stripMargin)
              Applicative[F].point(response)
          }
        }
      }
    }
  }

  /**
   * Wraps some routes using the 'AuthLogic' to supply the 'WebUser' to those routes
   *
   * @param authedRoutes the routes to wrap
   * @param logic        the auth logic
   * @tparam F
   * @return a kleisli representing the request->response in F
   */
  def wrap[F[_] : Monad](authedRoutes: AuthedRoutes[WebUser, F])(logic: AuthLogic[F]) = {
    val provider = authLogicAsProvider(logic)
    provideAuth(provider, authedRoutes)
  }

  def provideAuth[F[_] : Monad](auth: AuthProvider[F], authedRoutes: AuthedRoutes[WebUser, F]) = {
    val mw: AuthMiddleware[F, (JsonWebToken, Claims)] = middleWare[F](auth)
    mw.apply(authedRoutes)
  }

  def authLogicAsProvider[F[_] : Monad](logic: AuthLogic[F]): AuthProvider[F] = {
    Kleisli { request: Request[F] =>
      val authHeader: Option[Authorization] = request.headers.get(headers.Authorization)
      val xAccessHeader: Option[Authorization] = request.headers.get(CaseInsensitiveString(Settings.AccessHeader)).map { h =>
        Authorization(Credentials.Token(AuthScheme.Bearer, h.value))
      }

      logic(authHeader.orElse(xAccessHeader))
    }
  }

  private def middleWare[F[_] : Monad](auth: AuthProvider[F]): AuthMiddleware[F, (JsonWebToken, Claims)] = {
    val dsl = Http4sDsl[F]
    import dsl._

    val onAuthFailure: AuthedRoutes[String, F] = Kleisli(req => OptionT.liftF(Forbidden(req.context)))

    AuthMiddleware(auth, onAuthFailure)
  }
}
