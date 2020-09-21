package franz.rest

import cats.effect.IO
import org.http4s._
import org.http4s.circe._
import org.http4s.headers.Authorization


object Swagger4s {

  object implicits {

    implicit class RichSwaggerRequest(val r: Swagger.Request) extends AnyVal {
      def toHttp4s[F[_]]: Request[F] = Swagger4s.asRequest[F](r)

      def toHttp4s[F[_]](jwt: String): Request[F] = {
        toHttp4s[F].withHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, jwt)))
      }
    }

    implicit def asIO(r: Swagger.Request): Request[IO] = {
      r.toHttp4s[IO]
    }
  }

  def asRequest[F[_]](method: Method, url: String, queryParams: Map[String, String] = Map.empty): Request[F] = {
    val encoded = Uri.encode(url)
    val parsed = if (queryParams.isEmpty) {
      Uri.unsafeFromString(encoded)
    } else {
      Uri.unsafeFromString(encoded).withQueryParams(queryParams)
    }

    Request[F](method = method, uri = parsed)
  }

  def asRequest[F[_]](swagger: Swagger.Request): Request[F] = {
    import Method._
    swagger match {
      case Swagger.PostRequest(url, Some(body)) => asRequest(POST, url).withEntity(body)(jsonEncoder[F])
      case Swagger.GetRequest(url) => asRequest(GET, url)
      case Swagger.DeleteRequest(url, queryParams) => asRequest(DELETE, url, queryParams)
      case Swagger.PostRequest(url, None) => asRequest(POST, url)
    }
  }

}
