package franz.users

import cats.data.Kleisli
import cats.effect.Sync
import cats.{Applicative, Functor}
import franz.errors.{InvalidRequest, MissingPermissions}
import io.circe.Encoder
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Authorization

import scala.collection.immutable.ArraySeq

package object routes {

  type JsonWebToken = String

  type AuthLogic[F[_]] = Option[Authorization] => F[Either[String, WebUser]]
  type AuthProvider[F[_]] = Kleisli[F, Request[F], Either[String, WebUser]]


  def popPath(uri: Uri): Uri = {
    val pathSeq = ArraySeq.unsafeWrapArray(uriPath(uri))
    withPath(uri, pathSeq.init)
  }

  def uriPath(uri: Uri): Array[String] = uri.path.split("/", -1)
  def withPath(uri: Uri, newPath : Seq[String]) = uri.copy(path = newPath.mkString("/"))

  def asPermission[F[_]](req: ContextRequest[F, _]): String = asPermission[F](req.req)

  /**
   * @param req the http request
   * @return the http request as a permission
   */
  def asPermission[F[_]](req: Request[F]): String = asPermission(req.method, req.uri)

  def asPermission(method: Method, uri: Uri): String = s"${method.name}:${uri.path}"

  /**
   * Convenience method for setting our desired headers for success responses
   *
   * @param result
   * @param jwt
   * @param dsl
   * @tparam F
   * @tparam A
   * @return a response
   */
  def ok[F[_] : Sync, A: Encoder](result: A, jwt: String, dsl: Http4sDsl[F]): F[Response[F]] = {
    import io.circe.syntax._
    setToken(ok(result.asJson, dsl), jwt)
  }

  def setToken[F[_] : Functor](responseF: F[Response[F]], token: String) = {
    Functor[F].map(responseF) { response =>
      response.withHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, token)))
    }
  }

  def ok[F[_] : Sync, A: Encoder](result: A, dsl: Http4sDsl[F]): F[Response[F]] = {
    import dsl._
    import io.circe.syntax._
    import org.http4s.circe._
    Ok(result.asJson)
  }

  def notAuthorised[F[_] : Applicative](status: Status, missingPermission: String): F[Response[F]] = {
    import io.circe.syntax._
    import org.http4s.circe._

    val response = Response[F](status).withEntity(MissingPermissions(missingPermission).asJson)
    Applicative[F].pure(response)
  }
  def badRequest[F[_] : Applicative](reason: String): F[Response[F]] = {
    import io.circe.syntax._
    import org.http4s.circe._

    val response = Response[F](Status.BadRequest).withEntity(InvalidRequest(reason).asJson)
    Applicative[F].pure(response)
  }
}
