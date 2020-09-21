package franz

import cats.Monad
import cats.data.{Kleisli, OptionT}
import cats.effect.{IO, Sync}
import io.circe.Decoder
import org.http4s._

import scala.util.Try

package object rest {

  def parseBodyIO(responseT: OptionT[IO, Response[IO]]): IO[Option[String]] = parseBody[IO](responseT)

  def parseResponseBodyAs[F[_] : Sync : Monad, A: Decoder](resp: Response[F]): F[Try[A]] = {
    import cats.syntax.functor._
    parseResponseBody(resp).map(fromJson[A])
  }

  def parserFor[F[_] : Sync, A: Decoder] : Swagger.Parser[F, Response[F], A] = {
    Kleisli[F, Response[F], A] { response: Response[F] =>
      parseBodyAs[F, A](response)
    }
  }

  def parseBodyAs[F[_] : Sync, A: Decoder](resp: Response[F]): F[A] = {
    import cats.syntax.functor._
    parseResponseBody[F](resp).map { body =>
      require(resp.status == Status.Ok, s"Status was ${resp.status}: ${body}")
      franz.rest.fromJson[A](body).get
    }
  }

  def fromJson[A: Decoder](body: String) = io.circe.parser.decode[A](body).toTry

  def parseResponseBody[F[_] : Sync : Monad](resp: Response[F]): F[String] = {
    EntityDecoder.decodeString(resp)
  }


  def parseBody[F[_] : Sync : Monad](responseT: OptionT[F, Response[F]]): F[Option[String]] = {
    val optT = responseT.semiflatMap { x =>
      parseResponseBody(x)
    }

    optT.value
  }
}
