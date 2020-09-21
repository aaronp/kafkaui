package franz.users.routes

import cats.effect.{IO, Sync}
import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import franz.users.{JWTCache, Login, UserSwagger}
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Authorization
import org.http4s.{AuthScheme, Credentials, HttpRoutes, Response}

object LoginRoute extends StrictLogging {
  def apply[F[_] : Sync](service: Login.Service[F], jwt: JWTCache[F]): HttpRoutes[F] = {
    create(service, jwt.jwtCache, Http4sDsl[F])
  }

  private def create[F[_] : Sync](service: Login.Service[F], jwt: JWTCache.Service[F], dsl: Http4sDsl[F]): HttpRoutes[F] = {
    import dsl._
    implicit val decoder = jsonOf[F, Login.Request]
    HttpRoutes.of[F] {
      case req@POST -> Root / UserSwagger.UserLogin =>
        req.as[Login.Request].flatMap { request =>
          doLogin(service, jwt, request, dsl)
        }

      //  TODO - delete this noddy route
      case GET -> Root / UserSwagger.UserLogin / name => doLogin(service, jwt, Login.Request(name, ""), dsl)
    }
  }

  private def doLogin[F[_] : Sync](service: Login.Service[F],
                                   jwt: JWTCache.Service[F],
                                   request: Login.Request,
                                   dsl: Http4sDsl[F]): F[Response[F]] = {
    def asHttpResponse(response: Login.Response, httpResp: Response[F]): F[Response[F]] = {
      response.tokenAndUser.fold(Sync[F].pure(httpResp)) {
        case (token, user) =>
          val authHeader = Authorization(Credentials.Token(AuthScheme.Bearer, token))
          jwt.set(token, user).map { _ =>
            httpResp.withHeaders(authHeader)
          }
      }
    }

    for {
      response: Login.Response <- service.login(request)
      loginResponseHttp <- ok(response, dsl)
      http <- asHttpResponse(response, loginResponseHttp)
    } yield {
      http
    }
  }
}
