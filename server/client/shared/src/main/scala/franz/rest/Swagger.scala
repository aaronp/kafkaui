package franz.rest

import cats.ApplicativeError
import cats.data.{Kleisli, ReaderT}
import io.circe.{Decoder, Json}

import scala.concurrent.Future

/**
 * A bit like julien's 'endpoints' - just basic REST data structures which can be used from clients or servers
 */
object Swagger {

  sealed trait Request {
    def url: String
  }

  def get(url: String) = GetRequest(url)

  def post(url: String, body: Option[Json] = None) = PostRequest(url, body)

  def delete(url: String) = DeleteRequest(url)

  case class GetRequest(override val url: String) extends Request

  case class PostRequest(override val url: String, body: Option[Json]) extends Request

  object PostRequest {
    def apply(url: String, body: Json): PostRequest = PostRequest(url, Option(body))
  }

  case class DeleteRequest(override val url: String, queryParams: Map[String, String] = Map.empty) extends Request

  /**
   * Represents a generic means to send a Swagger.Request to get some result type 'A'
   * the 'A' will only vary between scalaJS (an XMLHttpRequest), JVM clients (typically a circe JSON response),
   * and Clients which wrap inner routes directly in tests (e.g. returning a WrappedResponse)
   * It's down to a [[Parser]] to then turn that type into an expected type
   */
  type Client[F[_], A] = ReaderT[F, Swagger.Request, A]
  val Client = Kleisli

  /**
   * A means to convert a response of type A into an F[B]
   *
   * @tparam F
   * @tparam A
   * @tparam B
   */
  type Parser[F[_], A, B] = Kleisli[F, A, B]

  def futureJsonParserFor[A: Decoder]: Swagger.Parser[Future, Json, A] = {
    Kleisli[Future, Json, A] { json =>
      json.as[A] match {
        case Left(err) => Future.failed(err)
        case Right(a) => Future.successful(a)
      }
    }
  }

  def parserForJson[F[_], A: Decoder](implicit appErr: ApplicativeError[F, Throwable]): Swagger.Parser[F, Json, A] = {
    Kleisli[F, Json, A] { json =>
      json.as[A] match {
        case Left(err) => appErr.raiseError(err)
        case Right(a) => appErr.pure(a)
      }
    }
  }
}
