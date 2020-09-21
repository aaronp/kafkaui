package franz.data.crud

import cats.{Applicative, ApplicativeError, Monad, ~>}
import franz.data.{CollectionName, QueryRange, QueryString, VersionedJson}
import franz.rest.Swagger
import franz.rest.Swagger.PostRequest
import io.circe.Json
import io.circe.syntax._

trait Search[F[_]] {
  def searchService: Search.Service[F]
}

object Search {

  trait Service[F[_]] extends Search[F] {
    self =>
    override def searchService: Search.Service[F] = self

    def search(request: Request): F[Response]

    def mapK[G[_]](implicit ev : F ~> G): Service[G] = {
      liftF[G] { request =>
        ev(self.search(request))
      }
    }
  }

  case class Request(collection: CollectionName, queryString: QueryString, limit: QueryRange)

  object Request {
    implicit val encoder = io.circe.generic.semiauto.deriveEncoder[Request]
    implicit val decoder = io.circe.generic.semiauto.deriveDecoder[Request]
  }

  case class Response(offset: Int, records: List[VersionedJson], total: Long) {
    def toOffset = offset + records.size
  }

  object Response {
    implicit val encoder = io.circe.generic.semiauto.deriveEncoder[Response]
    implicit val decoder = io.circe.generic.semiauto.deriveDecoder[Response]
  }

  def lift[F[_] : Applicative](thunk: Request => Response): Service[F] = {
    liftF(x => Applicative[F].pure(thunk(x)))
  }

  def liftF[F[_]](thunk: Request => F[Response]): Service[F] = new Service[F] {
    override def search(request: Request): F[Response] = {
      thunk(request)
    }
  }

  def client[F[_]](client: Swagger.Client[F, Json])(implicit monad: Monad[F], appErr: ApplicativeError[F, Throwable]): Service[F] = {
    val parser = Swagger.parserForJson[F, Response]
    liftF { request =>
      val http = PostRequest("/rest/search", request.asJson)
      Monad[F].flatMap(client.run(http))(x => parser(x))
    }
  }
}
