package franz.rest

import cats.effect.{Concurrent, IO, Sync}
import cats.{Functor, Monad}
import franz.Env
import franz.users.{Claims, JWTCache, WebUser}
import io.circe.Json
import org.http4s._
import org.http4s.headers.Authorization
import org.http4s.server.Router
import org.http4s.server.middleware.Logger

/**
 * A wrapper around some routes so we can use a "swagger" client to test our routes
 * just as we would if we were making real web requests
 */
object RouteClient {

  def loggedInClient(underlyingAuthedRoutes: AuthedRoutes[WebUser, IO]): RouteClient[IO] = {
    val env = Env()
    import env.implicits._
    loggedInClientF[IO](underlyingAuthedRoutes).unsafeRunSync()
  }

  def loggedInClientF[F[_] : Concurrent](underlyingAuthedRoutes: AuthedRoutes[WebUser, F]): F[RouteClient[F]] = {
    val cache = JWTCache.unsafe[F].jwtCache
    val appClient: RouteClient[F] = RouteClient[F](underlyingAuthedRoutes, cache)
    appClient.setJWT("a.b.c")
    import cats.syntax.functor._
    cache.set("a.b.c", Claims.forUser("test")).map { _ =>
      appClient
    }
  }


  def apply[F[_] : Monad : Concurrent](underlyingAuthedRoutes: AuthedRoutes[WebUser, F], users: JWTCache.Service[F]) = {
    forApp(routeAsApp(underlyingAuthedRoutes, users))
  }


  def forApp[F[_] : Functor](app: HttpApp[F]): RouteClient[F] = clientFor[F](app)

  /**
   * Represents a Swagger.Request -> Response[IO] which invokes the underlying HttpApp[IO].
   *
   * The client is stateful, in that any auth headers in the responses will be used in subsequent requests
   *
   * @param app
   * @return
   */
  def clientFor[F[_] : Functor](app: HttpApp[F]) = new RouteClient[F](app)

  def routeAsApp[F[_] : Monad : Concurrent](underlyingAuthedRoutes: AuthedRoutes[WebUser, F], users: JWTCache.Service[F]): HttpApp[F] = {
    val route = Auth.authedAsNormalRoutes[F](
      underlyingAuthedRoutes, // the routes we want to convert into Request[F] => Response[F]
      users
    )
    import org.http4s.implicits._
    val httpApp = Router("/rest" -> route).orNotFound
    Logger.httpApp(true, true)(httpApp)
  }

}

class RouteClient[F[_] : Functor](app: HttpApp[F]) {

  import cats.syntax.functor._

  var latestAuthToken: Option[String] = None

  import franz.rest.Swagger4s.implicits._

  def setJWT(jwt: String): Option[String] = setJWT(Option(jwt))

  def setJWT(jwt: Option[String]): Option[String] = {
    val b4 = latestAuthToken
    latestAuthToken = jwt.orElse(latestAuthToken)
    b4
  }

  def setAuthOnRequest(request: Request[F]): Request[F] = {
    latestAuthToken.fold(request) { jwt =>
      val withHeaders = request.withHeaders(headers.Authorization(Credentials.Token(AuthScheme.Bearer, jwt)))
      withHeaders.asInstanceOf[Request[F]]
    }
  }

  val client: Swagger.Client[F, Response[F]] = Swagger.Client { request: Swagger.Request =>
    val http4sRequest: Request[F] = setAuthOnRequest(request.toHttp4s[F])

    app(http4sRequest).map { resp =>
      setJWT {
        resp.headers.get(Authorization).collect {
          case Authorization(Credentials.Token(AuthScheme.Bearer, jwt)) => jwt
        }
      }
      resp
    }
  }

  def jsonClient(implicit sync: Sync[F], ev: F[_] =:= IO[_]): Swagger.Client[F, Json] = client.map { resp =>
    val parsed: F[Json] = parseBodyAs[F, Json](resp)
    val io: IO[Json] = ev(parsed).asInstanceOf[IO[Json]]
    io.unsafeRunSync()
  }
}
