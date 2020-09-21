package franz.data.crud

import cats.syntax.flatMap._
import cats.syntax.option._
import cats.{ApplicativeError, Monad, MonadError, ~>}
import franz.data._
import franz.data.crud.InsertRecord.Service
import franz.rest.Swagger
import franz.rest.Swagger.{DeleteRequest, GetRequest, PostRequest, _}
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}

import scala.reflect.ClassTag


/**
 * All the services required by a crud application
 *
 * Advanced matching (querying, writing association results) will be covered as a different application.
 *
 * Therefore we have the equivalent of:
 * {{{
 *
 *   POST /data/<collection>  # send a versioned record
 *   DELETE /data/<collection>/<id>  # delete record
 *   GET /data/<collection>/<id>  # get the latest version record
 *   GET /data/<collection>/<id>?version=v  # get version v of the record
 * }}}
 *
 * @param insertService a way to write things down in any collection
 * @param readService   a means to read records
 * @param listService   a means to list all the collections
 * @param deleteService a way to delete records
 * @tparam F the effect type
 */
final case class CrudServicesAnyCollection[F[_]](override val insertService: InsertRecord.Service[F, (CollectionName, VersionedJson), VersionedJsonResponse],
                                                 override val readService: ReadRecord.Service[F, RecordCoords, Option[VersionedJson]],
                                                 override val listService: ListRecords.Service[F, List[CollectionName]],
                                                 override val deleteService: DeleteRecord.Service[F, (CollectionName, Id), Option[VersionedJson]])
  extends InsertRecord[F, (CollectionName, VersionedJson), VersionedJsonResponse]
    with DeleteRecord[F, (CollectionName, String), Option[VersionedJson]]
    with ReadRecord[F, RecordCoords, Option[VersionedJson]]
    with ListRecords[F, List[CollectionName]] {

  def mapK[G[_]](implicit ev: F ~> G): CrudServicesAnyCollection[G] = {
    CrudServicesAnyCollection[G](
      insertService.mapInsertK[G],
      readService.mapReadK[G],
      listService.mapListK[G],
      deleteService.mapDeleteK[G])
  }

  def insert[A: Encoder](collection: CollectionName, record: VersionedRecord[A]): F[VersionedJsonResponse] = {
    insertService.insert(collection -> record.withData(record.data.asJson))
  }

  /**
   * Fix the collection to a specific collection
   *
   * @param fixedCollection
   */
  case class forCollection(fixedCollection: CollectionName) {
    def insert: Service[F, VersionedJson, VersionedJsonResponse] = {
      insertService.contractMapInsert[VersionedJson] { record =>
        (fixedCollection, record)
      }
    }

    def latest(id: Id): F[Option[VersionedJson]] = {
      read.read(id -> RecordVersion.latest)
    }

    def read: ReadRecord.Service[F, (Id, RecordVersion), Option[VersionedJson]] = {
      readService.contractMapRead[(Id, RecordVersion)] {
        case (id, version) => RecordCoords(fixedCollection, id, version)
      }
    }

    def delete: DeleteRecord.Service[F, Id, Option[VersionedJson]] = {
      deleteService.contractMapDelete[Id] { id =>
        (fixedCollection, id)
      }
    }

    /**
     * asCrudServices
     *
     * @param crudListService the liset function for this CrudService values
     * @param F
     * @tparam A
     * @return the CrudServices for this collection
     */
    def asCrudServices[A: Encoder : Decoder](crudListService: ListRecords.Service[F, List[Id]])(implicit F: MonadError[F, Throwable]): CrudServices[F, A] = {
      val crudInsert = InsertRecord.liftF[F, VersionedRecord[A], InsertResponse[VersionedRecord[A]]] { record =>
        F.map(insert.insert(record.mapToJson)) { response =>
          response.map(_ => record)
        }
      }

      val crudReadService: ReadRecord.Service[F, RecordCoords, Option[VersionedRecord[A]]] = {
        ReadRecord.liftF[F, RecordCoords, Option[VersionedRecord[A]]] { weirdInputCoords =>
          // this is weird/wrong
          val fixedCoords = weirdInputCoords.copy(collection = fixedCollection)
          F.flatMap(readService.read(fixedCoords)) {
            case None => F.pure(none[VersionedRecord[A]])
            case Some(versionedJson: VersionedJson) =>
              versionedJson.data.as[A] match {
                case Left(err) => F.raiseError(new IllegalStateException(s"Couldn't unmarshal '$fixedCoords': $err"))
                case Right(a) => F.pure(versionedJson.map(_ => a).some)
              }
          }
        }
      }

      val crudDeleteService: DeleteRecord.Service[F, Id, Option[VersionedRecord[A]]] = {
        delete.flatMapDeleteResult {
          case None => F.pure(none[VersionedRecord[A]])
          case Some(deleted) =>
            deleted.data.as[A] match {
              case Left(err) => F.raiseError(new IllegalStateException(s"Couldn't unmarshal '${deleted.data}': $err"))
              case Right(a) => F.pure(deleted.map(_ => a).some)
            }
        }
      }

      CrudServices[F, A](
        crudInsert,
        crudReadService,
        crudListService,
        crudDeleteService
      )
    }
  }

  object forCollection {
    def apply[A: ClassTag] = new forCollection(franz.data.collectionNameFor[A])
  }

  def asCrudServices[A: ClassTag : Decoder : Encoder](listIds: ListRecords.Service[F, List[Id]])(implicit F: MonadError[F, Throwable]): CrudServices[F, A] = {
    forCollection.apply[A].asCrudServices[A](listIds)
  }

  def asCrudServices[A: ClassTag : Decoder : Encoder](collectionName: CollectionName)(listIds: ListRecords.Service[F, List[Id]])(implicit F: MonadError[F, Throwable]): CrudServices[F, A] = {
    forCollection(collectionName).asCrudServices[A](listIds)
  }
}


object CrudServicesAnyCollection {

  val VersionQueryParam = "version"

  def client[F[_]](namespace: String, client: Swagger.Client[F, Json])(implicit monad: Monad[F], appErr: ApplicativeError[F, Throwable]): CrudServicesAnyCollection[F] = {
    val namespaced = ForNamespace(namespace)

    new CrudServicesAnyCollection[F](
      insertService = namespaced.InsertClient(client, parserForJson[F, VersionedJsonResponse]),
      readService = namespaced.RecordVersionClient(client, parserForJson[F, Option[VersionedJson]]),
      listService = namespaced.ListCollectionsClient(client, parserForJson[F, List[CollectionName]]),
      deleteService = namespaced.DeleteClient(client, parserForJson[F, Option[VersionedJson]])
    )
  }

  /**
   * This namespaces the route (e.g. 'data' for generic data), NOT the collection name
   *
   * @param namespace
   */
  case class ForNamespace(namespace: String) {

    final case class InsertClient[F[_] : Monad, A: Encoder, R](client: Swagger.Client[F, A], parser: Swagger.Parser[F, A, R]) extends Service[F, (CollectionName, VersionedJson), R] {
      override def insert(requestPear: (CollectionName, VersionedJson)): F[R] = {
        val (collection, data) = requestPear
        client.run(PostRequest(s"/rest/$namespace/$collection", data.asJson)).flatMap { result: A =>
          parser.apply(result)
        }
      }
    }

    final case class RecordVersionClient[F[_] : Monad, A](client: Client[F, A], parser: Parser[F, A, Option[VersionedJson]]) extends ReadRecord.Service[F, RecordCoords, Option[VersionedJson]] {
      override def read(coords: RecordCoords): F[Option[VersionedJson]] = {
        import coords._
        val request = GetRequest(s"/rest/$namespace/$collection/$id?$VersionQueryParam=${version.queryValue}")
        client.run(request).flatMap(parser.apply)
      }
    }

    final case class ListCollectionsClient[F[_] : Monad, A, R](client: Client[F, A], parser: Parser[F, A, List[CollectionName]]) extends ListRecords.Service[F, List[CollectionName]] {
      override def list(range: QueryRange) = {
        client.run(GetRequest(s"/rest/$namespace?from=${range.from}&limit=${range.limit}")).flatMap(parser.apply)
      }
    }

    final case class DeleteClient[F[_] : Monad, A](client: Swagger.Client[F, A], parser: Parser[F, A, Option[VersionedJson]]) extends DeleteRecord.Service[F, (CollectionName, Id), Option[VersionedJson]] {
      override def delete(record: (CollectionName, Id)) = {
        val (collection, id) = record
        client.run(DeleteRequest(s"/rest/$namespace/$collection/$id")).flatMap(parser.apply)
      }
    }

  }

}
