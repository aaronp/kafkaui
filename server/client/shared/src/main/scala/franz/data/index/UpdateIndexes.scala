package franz.data.index

import cats.Parallel
import franz.data.crud.{DeleteRecord, InsertRecord, ReadRecord}
import franz.data.{CollectionName, Id, VersionedJsonResponse, _}

/**
 * What we're trying to do:
 *
 *
 * wrap up an insert service so that we:
 * $ index the values when we can successfully write a new record
 * $ remove old indices from the previous record
 *
 * e.g. if we write version one:
 * {{{
 *   name : alpha
 *   age : 55
 *   email : em@il.com
 * }}}
 *
 * followed by version two:
 *
 * {{{
 *   name : beta
 *   age : 55
 *   email : upd@ted.com
 *   newField : true
 * }}}
 *
 * Then the following should happen:
 *
 * First write of version 1:
 * We don't find a previous version, so we just index everything:
 * $  alpha ->       (<collection>, "name", <id>, 1)
 * $  55 ->          (<collection>, "age", <id>, 1) // the creation of this should replace the existing version 1
 * $  em@il.com ->   (<collection>, "email", <id>, 1)
 *
 * Second write, version 2:
 *
 * We create the indices:
 * $  beta ->        (<collection>, "name", <id>, 2)
 * $  55 ->          (<collection>, "age", <id>, 2) // the creation of this should replace the existing version 1
 * $  upd@ted.com -> (<collection>, "email", <id>, 2)
 *
 *
 * And should notice that we need to REMOVE the entries for 'alpha' and 'em@ail.com'
 * $  alpha ->       (<collection>, "name", <id>, 1)
 * $  em@il.com ->   (<collection>, "email", <id>, 1)
 *
 *
 * To accomplish this, we will need:
 *
 * $ have a writer which we can wrap, so successful writes result in the update of indices
 * $ the ability to find a previous version which we can then interrogate for its indices
 * $ an indexer so we can add/remove indices
 *
 * What we expose:
 * $ a writer of the same signature of the wrapper writer, but this one will update indices
 *
 */
private[index] object UpdateIndexes {

  type Indices = Seq[(IndexValue, ReferenceToValue, IndexedValue)]
  type RemovedIndices = Seq[(IndexValue, ReferenceToValue, Boolean)]
  type IndexWriteResponse = (Indices, VersionedJsonResponse)
  type IndexDeleteResponse = (RemovedIndices, Option[VersionedJson])

  import cats.syntax.functor._

  def indexedDelete[F[_] : Parallel](underlyingDelete: DeleteRecord.Service[F, (CollectionName, Id), Option[VersionedJson]], indexer: Indexer.Service[F]): DeleteRecord.Service[F, (CollectionName, Id), Option[VersionedJson]] = {
    implicit val M = Parallel[F].monad
    DeleteRecord.map(deleteIndices(underlyingDelete, indexer))(_._2)
  }

  def deleteIndices[F[_] : Parallel](underlyingDelete: DeleteRecord.Service[F, (CollectionName, Id), Option[VersionedJson]],
                                     indexer: Indexer.Service[F]): DeleteRecord.Service[F, (CollectionName, Id), IndexDeleteResponse] = {
    implicit val M = Parallel[F].monad
    DeleteRecord.liftF[F, (CollectionName, Id), IndexDeleteResponse] {
      case tuple@(collection, _) =>
        import cats.implicits._
        underlyingDelete.delete(tuple).flatMap {
          case deleteResponse@Some(record) =>
            M.map(indexer.remove(collection, record)) { removed =>
              (removed, deleteResponse)
            }
          case None => M.point((Nil -> None))
        }
    }
  }

  def indexedWriter[F[_] : Parallel](underlyingWriter: InsertRecord.Service[F, (CollectionName, VersionedJson), VersionedJsonResponse],
                                 previousVersionReader: ReadRecord.Service[F, RecordCoords, Option[VersionedJson]],
                                 compoundIndicesReader: ReadRecord.Service[F, CollectionName, Seq[CompoundIndex]],
                                 indexer: Indexer.Service[F]
                                ): InsertRecord.Service[F, (CollectionName, VersionedJson), VersionedJsonResponse] = {
    implicit val M = Parallel[F].monad
    val responseF = indexOnUpdate(underlyingWriter, previousVersionReader, compoundIndicesReader, indexer)
    InsertRecord.map(responseF)(_._2)
  }

  def indexOnUpdate[F[_] : Parallel](underlyingWriter: InsertRecord.Service[F, (CollectionName, VersionedJson), VersionedJsonResponse],
                                 readRecord: ReadRecord.Service[F, RecordCoords, Option[VersionedJson]],
                                 compoundIndicesReader: ReadRecord.Service[F, CollectionName, Seq[CompoundIndex]],
                                 indexer: Indexer.Service[F]
                                ): InsertRecord.Service[F, (CollectionName, VersionedJson), IndexWriteResponse] = {
    implicit val M = Parallel[F].monad
    import cats.syntax.parallel._
    InsertRecord.liftF[F, (CollectionName, VersionedJson), IndexWriteResponse] {
      case (collectionName, originalRecord) =>

        val previousVersionF: F[Option[VersionedJson]] = readRecord.read(RecordCoords.previous(collectionName, originalRecord.id, originalRecord.version))

        // we need to index BEFORE insert. So much for that bit of parallelism :-(
        import cats.syntax.flatMap._

        val insertF = compoundIndicesReader.read(collectionName).flatMap { compoundIndices =>
          val indexedRecord = CompoundIndex(originalRecord, compoundIndices)
          underlyingWriter.insert(collectionName, indexedRecord)
        }

        val indexF: F[F[IndexWriteResponse]] = (previousVersionF, insertF).parMapN {
          // found a previous version, update the indices
          case (Some(previous), response: VersionedJsonResponse) =>

            response.toEither match {
              case Left(_) => M.point(Nil -> response)
              case Right(inputRecord) =>
                indexer.update(collectionName, previous, inputRecord).map { indexResponse =>
                  (indexResponse, response)
                }
            }

          // no 'previous' version - index the whole thing
          case (None, response) =>
            response.toEither match {
              case Left(_) => M.point(Nil -> response)
              case Right(inputRecord) =>
                indexer.index(collectionName, inputRecord).map { indexResponse =>
                  (indexResponse, response)
                }
            }
        }
        M.flatten(indexF)
    }
  }
}
