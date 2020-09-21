package franz.data.index

import cats.Applicative
import cats.effect.Sync
import franz.data.crud._
import franz.data.index.Indexer.Service
import franz.data.{IndexValue, QueryRange, VersionedRecord}

object IndexerInstance {

  case class Input[F[_]](insertService: InsertRecord.Service[F, VersionedRecord[IndexedValue], InsertResponse[VersionedRecord[IndexedValue]]],
                         deleteService: DeleteRecord.Service[F, IndexValue, Option[VersionedRecord[IndexedValue]]],
                         readService: ReadRecord.Service[F, IndexValue, Option[VersionedRecord[IndexedValue]]],
                         listService: ListRecords.Service[F, List[VersionedRecord[IndexedValue]]]
                        ) extends InsertRecord[F, VersionedRecord[IndexedValue], InsertResponse[VersionedRecord[IndexedValue]]]
    with DeleteRecord[F, String, Option[VersionedRecord[IndexedValue]]]
    with ReadRecord[F, String, Option[VersionedRecord[IndexedValue]]]
    with ListRecords[F, List[VersionedRecord[IndexedValue]]]

  def apply[F[_] : Sync](database: VersionedCache[F, String, VersionedRecord[IndexedValue]], tooManyValuesThreshold: Int): IndexerInstance[F] = {
    val input = Input(database.insertService, database.deleteService, database.readService, database.listService)

    new IndexerInstance[F](input, tooManyValuesThreshold)
  }
}

class IndexerInstance[F[_] : Sync](database: IndexerInstance.Input[F], tooManyValuesThreshold: Int) extends Service[F] {

  import cats.syntax.flatMap._
  import cats.syntax.functor._

  override def read(indexValue: String): F[Option[IndexedValue]] = database.readService.read(indexValue).map(_.map(_.data))

  def list(range: QueryRange): F[List[VersionedRecord[IndexedValue]]] = {
    database.listService.list(range)
  }

  override def addIndex(key: IndexValue, value: ReferenceToValue): F[IndexedValue] = {
    addIndexOrRetry(key, value, 10)
  }

  /**
   * The scenario is this: we are updated a distributed database which could (and likely will) have some race-conditions
   * for updating indices.
   *
   * We need to bake-in the ability to retry this loop:
   *
   * 1) read the indices
   * 2) update it based on our value
   * 3) save it back
   *
   * When saving it back fails, we want to retry up to 'retriesRemaining' times
   *
   * @param key
   * @param value
   * @param retriesRemaining
   * @return
   */
  private def addIndexOrRetry(key: IndexValue, value: ReferenceToValue, retriesRemaining: Int): F[IndexedValue] = {
    import VersionedRecord.syntax._

    def indexResult(response: InsertResponse[VersionedRecord[IndexedValue]]): F[IndexedValue] = {
      response.toEither match {
        case Left(e) if retriesRemaining > 0 => addIndexOrRetry(key, value, retriesRemaining - 1)
        case Left(err) => Sync[F].raiseError(err)
        case Right(response) => Applicative[F].pure(response.data)
      }
    }

    database.readService.read(key).flatMap {
      case Some(VersionedRecord(tooMany@TooManyValues(_), _, _, _, _)) => Applicative[F].pure(tooMany)
      case Some(versioned@VersionedRecord(refs: FixedReferences, _, _, _, _)) =>
        refs.insertOrReplace(value, tooManyValuesThreshold) match {
          case (updated, _) =>
            import cats.syntax.flatMap._
            val newIndex: VersionedRecord[IndexedValue] = versioned.incVersion.withData[IndexedValue](updated)
            database.insertService.insert(newIndex).flatMap(indexResult)
        }
      case None =>
        val first = IndexedValue(value).versionedRecord(id = key)
        database.insertService.insert(first).flatMap(indexResult)
    }
  }

  override def removeIndex(key: IndexValue, value: ReferenceToValue): F[Boolean] = {
    Sync[F].flatMap(database.readService.read(key)) {
      case None => Sync[F].point(false)
      case Some(VersionedRecord(TooManyValues(_), _, _, _, _)) => Sync[F].point(false)
      case Some(versioned@VersionedRecord(originalRefs@FixedReferences(_), _, _, _, _)) =>
        originalRefs.remove(value) match {
          case (updated, true) =>
            val newIndex = versioned.incVersion.withData(updated)
            database.insertService.insert(newIndex).map { insertResponse =>
              insertResponse.isSuccess
            }
          case (_, false) => Sync[F].point(false)
        }
    }
  }
}
