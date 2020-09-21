package franz.data.index

import cats.effect.Sync
import cats.implicits._
import cats.{Parallel, ~>}
import franz.data._
import franz.data.crud.{ListRecords, ReadRecord, VersionedCache}
import io.circe.{Encoder, Json}

trait Indexer[F[_]] {

  def indexer: Indexer.Service[F]
}

object Indexer {

  /**
   * The indexer service is what writes down/removes indices for a particular value.
   *
   * If some json has { "foo" : "bar" }, then the indexer would write a record for that collection/id/version for 'bar'
   *
   * @tparam F the effect type
   */
  trait Service[F[_]] extends Indexer[F] with ReadRecord.Service[F, IndexValue, Option[IndexedValue]] with ListRecords.Service[F, List[VersionedRecord[IndexedValue]]]{
    self =>
    override def indexer: Indexer.Service[F] = self

    type BatchResult = (IndexValue, ReferenceToValue, IndexedValue)
    type RemoveResult = (IndexValue, ReferenceToValue, Boolean)

    final def mapK[G[_]](implicit ev: F ~> G): Service[G] = {
      new Service[G] {
        override def addIndex(key: IndexValue, reference: ReferenceToValue): G[IndexedValue] = ev(self.addIndex(key, reference))
        override def removeIndex(key: IndexValue, reference: ReferenceToValue): G[Boolean] = ev(self.removeIndex(key, reference))
        override def read(query: IndexValue): G[Option[IndexedValue]] = ev(self.read(query))
        override def list(range: QueryRange): G[List[VersionedRecord[IndexedValue]]] = ev(self.list(range))
      }
    }

    /**
     * The service is implied to be a specific to a particular index (e.g. data.foo.bar)
     * The 'id' is the value of the jpath 'data.foo.bar' for a particular record
     *
     * @param key       the string representation of a value
     * @param reference the reference to that value
     * @return true if the value was added
     */
    def addIndex(key: IndexValue, reference: ReferenceToValue): F[IndexedValue]

    /** @param key
     * @param reference the reference to the index value
     * @return true if the index reference was removed successfully
     */
    def removeIndex(key: IndexValue, reference: ReferenceToValue): F[Boolean]

    /**
     * Removes old indices from the previous value and updates the new ones
     *
     * @param collection
     * @param previous
     * @param newRecord
     * @param P the parallel instance
     * @tparam A
     * @return
     */
    final def update[A: Encoder](collection: CollectionName, previous: VersionedRecord[A], newRecord: VersionedRecord[A])(implicit P: Parallel[F]): F[Seq[BatchResult]] = {
      implicit val M = P.monad
      val returnValue: F[Seq[BatchResult]] = M.flatMap(index(collection, newRecord)) { newIndices: Seq[(IndexValue, ReferenceToValue, IndexedValue)] =>
        val newValues: Set[IndexValue] = newIndices.map(_._1).toSet
        val oldReferences: List[(IndexValue, ReferenceToValue)] = referencesFor(collection, previous)

        // any index values which we no longer have in our update should be removed
        val removes: List[F[Boolean]] = oldReferences.collect {
          case (value, ref) if !newValues.contains(value) => indexer.removeIndex(value, ref)
        }

        if (removes.isEmpty) {
          M.point(newIndices)
        } else {
          removes.parSequence.map { _ =>
            newIndices
          }
        }
      }
      returnValue
    }

    /**
     * Removes the references to this record
     *
     * @param collection
     * @param removedRecord
     * @param P a parallel evidence for F
     * @tparam A
     * @return
     */
    final def remove[A: Encoder](collection: CollectionName, removedRecord: VersionedRecord[A])(implicit P: Parallel[F]) = {
      val references: List[(IndexValue, ReferenceToValue)] = referencesFor(collection, removedRecord)
      removeAll(references)
    }

    final def index[A: Encoder](collection: CollectionName, entity: VersionedRecord[A])(implicit P: Parallel[F]): F[List[BatchResult]] = {
      val references: List[(IndexValue, ReferenceToValue)] = referencesFor(collection, entity)
      indexAll(references)
    }

    /**
     * @param entity the entity to match
     * @param P
     * @tparam A
     * @return All matching associations for this entity
     */
    final def valueAssociations[A: Encoder](entity: A)(implicit P: Parallel[F]): F[RecordAssociations] = {
      implicit val M = P.monad
      valuesFor(entity).parTraverse {
        case (path: ValuePath, indexValue: String) => read(indexValue).map(FieldAssociation(path, indexValue, _))
      }.map(RecordAssociations.apply)
    }


    def matchRecordReader(implicit P: Parallel[F]): ReadRecord.Service[F, Json, RecordAssociations] = ReadRecord.liftF[F, Json, RecordAssociations](valueAssociations)

    final def indexAll(references: List[(IndexValue, ReferenceToValue)])(implicit P: Parallel[F]): F[List[BatchResult]] = {
      implicit val M = P.monad

      // Each indexed write could be in a race w/ other inserts occurring at the same time.
      // They could even interfere w/ themselves if there are two entries w/ the same value in the same document!
      // the latter (multiple values in the same doc) we can try and fix by checking here. There's not much we can do
      // (other than retry the 'add' operation) if there are other indices being added concurrently on different machines
      //
      // we could try and find out which duplicates there are, but we'll leave that as a future optimization
      // TODO: group references by unique value and map/par map the inserts appropriately
      //

      val inserts: List[F[List[(IndexValue, ReferenceToValue, IndexedValue)]]] = references.map {
        case (value, reference) =>
          val addResult: F[IndexedValue] = addIndex(value, reference)
          addResult.map { result =>
            List((value, reference, result))
          }
      }
      if (inserts.isEmpty) {
        M.point(Nil)
      } else {
        inserts.parFlatSequence
      }
    }

    final def removeAll(references: List[(CollectionName, ReferenceToValue)])(implicit P: Parallel[F]): F[List[RemoveResult]] = {
      implicit val M = P.monad
      val deletions: List[F[List[(CollectionName, ReferenceToValue, Boolean)]]] = references.map {
        case (value, reference) => removeIndex(value, reference).map { result =>
          List((value, reference, result))
        }
      }
      if (deletions.isEmpty) {
        M.pure(Nil)
      } else {
        deletions.parFlatSequence
      }
    }
  }

  object Service {
    def apply[F[_] : Sync](tooManyValuesThreshold: Int) = {
      val cache: VersionedCache[F, String, VersionedRecord[IndexedValue]] = VersionedCache.unsafe[F, String, VersionedRecord[IndexedValue]]
      forCache(cache, tooManyValuesThreshold)
    }

    def forCache[F[_] : Sync](cache: VersionedCache[F, String, VersionedRecord[IndexedValue]], tooManyValuesThreshold: Int): IndexerInstance[F] = {
      IndexerInstance(cache, tooManyValuesThreshold)
    }
  }
}
