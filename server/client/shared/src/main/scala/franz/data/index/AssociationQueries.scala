package franz.data.index

import cats.implicits._
import cats.{ApplicativeError, Monad, ~>}
import franz.data._
import franz.data.crud.{CrudServices, CrudServicesAnyCollection, ReadRecord}
import franz.rest.Swagger
import franz.rest.Swagger.{GetRequest, Parser, PostRequest, parserForJson}
import io.circe.Json

/**
 * A reference to all the services used by the indexer.
 *
 * Those underlying services could be REST clients or the back-end services served by the REST routes
 *
 * @param readIndex   a means to read the indices for a value
 * @param matchRecord service to search associated records for arbitrary POSTed data
 * @param matchEntity service to find associated records for some saved data
 * @tparam F
 */
case class AssociationQueries[F[_]](readIndex: ReadRecord.Service[F, IndexValue, Option[IndexedValue]],
                                    matchRecord: ReadRecord.Service[F, Json, RecordAssociations],
                                    matchEntity: ReadRecord.Service[F, RecordCoords, RecordAssociations]
                                ) {
  def mapK[G[_]](implicit ev: F ~> G): AssociationQueries[G] = {
    AssociationQueries[G](
      readIndex.mapReadK[G],
      matchRecord.mapReadK[G],
      matchEntity.mapReadK[G])
  }
}

object AssociationQueries {

  val Namespace = "index"

  def client[F[_]](client: Swagger.Client[F, Json])(implicit monad: Monad[F], appErr: ApplicativeError[F, Throwable]): AssociationQueries[F] = {
    new AssociationQueries[F](
      readIndex = ReadIndexClient(client, parserForJson[F, Option[IndexedValue]]),
      matchRecord = MatchRecordClient(client, parserForJson[F, RecordAssociations]),
      matchEntity = MatchEntityClient(client, parserForJson[F, RecordAssociations])
    )
  }

  def readIndexRequest(indexValue: IndexValue): GetRequest = GetRequest(s"/rest/$Namespace/index/$indexValue")

  final case class ReadIndexClient[F[_] : Monad, A](client: Swagger.Client[F, A], parser: Parser[F, A, Option[IndexedValue]]) extends ReadRecord.Service[F, IndexValue, Option[IndexedValue]] {
    override def read(query: IndexValue): F[Option[IndexedValue]] = {
      client.run(readIndexRequest(query)).flatMap(parser.apply)
    }
  }

  def matchEntityRequest(coords: RecordCoords): GetRequest = GetRequest(s"/rest/$Namespace/match/${coords.collection}/${coords.id}?${CrudServicesAnyCollection.VersionQueryParam}=${coords.version.queryValue}")

  final case class MatchEntityClient[F[_] : Monad, A](client: Swagger.Client[F, A], parser: Parser[F, A, RecordAssociations]) extends ReadRecord.Service[F, RecordCoords, RecordAssociations] {
    override def read(coords: RecordCoords) = {
      client.run(matchEntityRequest(coords)).flatMap(parser.apply)
    }
  }

  def matchRequest(record: Json): PostRequest = PostRequest(s"/rest/$Namespace", record)

  final case class MatchRecordClient[F[_] : Monad, A](client: Swagger.Client[F, A], parser: Parser[F, A, RecordAssociations]) extends ReadRecord.Service[F, Json, RecordAssociations] {
    override def read(record: Json): F[RecordAssociations] = {
      client.run(matchRequest(record)).flatMap(parser.apply)
    }
  }
}
