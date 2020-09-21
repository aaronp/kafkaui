package franz.data.crud

import cats.{ApplicativeError, Monad, ~>}
import cats.effect.Sync
import cats.implicits._
import franz.data._
import franz.data.crud.CrudServicesAnyCollection.VersionQueryParam
import franz.data.crud.InsertRecord.Service
import franz.rest.Swagger
import franz.rest.Swagger._
import io.circe.{Decoder, Encoder, Json}
import io.circe.syntax._

/**
 * All the services required by a crud application
 *
 * Advanced matching (querying, writing association results) will be covered as a different application.
 *
 * @param insertService
 * @param readService  TODO: this is weird/wrong. A 'RecordCoords' is for *any* collection ... ?
 * @param listService
 * @param deleteService
 * @tparam F the effect type
 */
final case class CrudServices[F[_], A: Encoder : Decoder](override val insertService: InsertRecord.Service[F, VersionedRecord[A], VersionedResponse[A]],
                                                          override val readService: ReadRecord.Service[F, RecordCoords, Option[VersionedRecord[A]]],
                                                          override val listService: ListRecords.Service[F, List[Id]],
                                                          override val deleteService: DeleteRecord.Service[F, Id, Option[VersionedRecord[A]]])
  extends InsertRecord[F, VersionedRecord[A], VersionedResponse[A]]
    with DeleteRecord[F, Id, Option[VersionedRecord[A]]]
    with ReadRecord[F, RecordCoords, Option[VersionedRecord[A]]]
    with ListRecords[F, List[Id]] {
  def insert(record: VersionedRecord[A]): F[VersionedResponse[A]] = insertService.insert(record)

  def mapK[G[_]](implicit ev: F ~> G): CrudServices[G, A] = {
    CrudServices[G, A](
      insertService.mapInsertK[G],
      readService.mapReadK[G],
      listService.mapListK[G],
      deleteService.mapDeleteK[G])
  }

}

object CrudServices {
  def client[F[_], A: Encoder : Decoder](namespace: CollectionName, client: Swagger.Client[F, Json])(implicit monad : Monad[F], appErr : ApplicativeError[F, Throwable]): CrudServices[F, A] = {
    val namespaced = ForNamespace(namespace)

    new CrudServices[F, A](
      insertService = namespaced.InsertClient(client, parserForJson[F, VersionedResponse[A]]),
      readService = namespaced.RecordVersionClient(client, parserForJson[F, Option[VersionedRecord[A]]]),
      listService = namespaced.ListCollectionsClient(client, parserForJson[F, List[Id]]),
      deleteService = namespaced.DeleteClient(client, parserForJson[F, Option[VersionedRecord[A]]])
    )
  }

  case class ForNamespace(collection: CollectionName) {
    final case class InsertClient[F[_] : Monad, A: Encoder](client: Swagger.Client[F, Json], parser: Swagger.Parser[F, Json, VersionedResponse[A]]) extends Service[F, VersionedRecord[A], VersionedResponse[A]] {
      import cats.syntax.flatMap._
      override def insert(data: VersionedRecord[A]): F[VersionedResponse[A]] = {
        client.run(PostRequest(s"/rest/$collection", data.asJson)).flatMap(parser.apply)
      }
    }

    final case class RecordVersionClient[F[_] : Monad, A](client: Client[F, Json], parser: Parser[F, Json, Option[VersionedRecord[A]]]) extends ReadRecord.Service[F, RecordCoords, Option[VersionedRecord[A]]] {
      override def read(coords: RecordCoords): F[Option[VersionedRecord[A]]] = {
        client.run(GetRequest(s"/rest/$collection/${coords.id}?$VersionQueryParam=${coords.version.queryValue}")).flatMap(parser.apply)
      }
    }

    final case class ListCollectionsClient[F[_] : Monad, A, R](client: Client[F, Json], parser: Parser[F, Json, List[Id]]) extends ListRecords.Service[F, List[Id]] {
      override def list(range: QueryRange): F[List[Id]] = {
        client.run(GetRequest(s"/rest/$collection?from=${range.from}&limit=${range.limit}")).flatMap(parser.apply)
      }
    }

    final case class DeleteClient[F[_] : Monad, A](client: Swagger.Client[F, Json], parser: Parser[F, Json, Option[VersionedRecord[A]]]) extends DeleteRecord.Service[F, Id, Option[VersionedRecord[A]]] {
      override def delete(id: Id): F[Option[VersionedRecord[A]]] = client.run(DeleteRequest(s"/rest/$collection/$id")).flatMap(parser.apply)
    }
  }
}
