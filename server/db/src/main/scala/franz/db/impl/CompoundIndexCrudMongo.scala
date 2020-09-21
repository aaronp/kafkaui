package franz.db.impl

import com.typesafe.scalalogging.StrictLogging
import franz.data._
import franz.data.index.CompoundIndexCrud.Writer
import franz.data.index.{CompoundIndex, CompoundIndexCrud}
import io.circe.Json
import mongo4m.LowPriorityMongoImplicits
import monix.eval.Task
import monix.reactive.{Consumer, Observable}

/**
 * Ultimately we want a normal "insert indices" service which, if successful, will update old records with the new index.
 *
 * To do that, we need to:
 * 1) find the delta between the "before" and "after" indices
 * this could be indexes which are for the same path but have had their computation changed (e.g. we've added a 'to upper' function).
 *
 * 2) find the records which:
 * a) are missing new indices
 * b) just every fucking record if the index has changed
 *
 * 3) for each of those, re-save using an insert service ... presumably one which does all the shit we want (e.g. does the indexing).
 * this is going to suck - 'cause almost every index will be the same, bar this new field.
 * We might be able to get away with just writing down the first field.
 *
 * keep in mind we'll have to re-read/retry for version issues on the write if a user makes a subsequent update between our
 * query result and our write
 *
 */
object CompoundIndexCrudMongo extends LowPriorityMongoImplicits with StrictLogging {

  private def cores = Runtime.getRuntime.availableProcessors()

  private def reindex(collection: CollectionName, writer: Writer[Task])(record: VersionedRecord[Json]): Task[Unit] = {
    logger.info(s"Reindexing $collection: $record")
    val task: Task[VersionedJsonResponse] = writer.insert(collection -> record)
    // TODO - what happens if we encounter a write while we're re-indexing?
    //    task.onErrorRecoverWith {
    //      case err => ???
    //    }
    task.void
  }

  /**
   *
   * @param mongo
   * @param indexingWriter given some indices, return a writer which persists records using those indices
   * @return
   */
  def apply(mongo: VersionedRecordsMongo)(indexingWriter: Seq[CompoundIndex] => Writer[Task]): CompoundIndexCrud[Task] = {
    val underlyingMongoCrud: CrudServicesAnyCollectionMongo = CrudServicesAnyCollectionMongo(mongo)

    def fullReindex(collection: CollectionName, indices: Seq[CompoundIndex]): Task[Unit] = {
      val records: Observable[VersionedRecord[Json]] = mongo.latest[Json](collection).list().dump(s"reindex $collection")

      val reindexWriter: Writer[Task] = indexingWriter(indices)
      val consumer = Consumer.foreachParallelTask[VersionedRecord[Json]](cores)(reindex(collection, reindexWriter))

      records.map(_.incVersion).consumeWith(consumer)
    }

    CompoundIndexCrud[Task](
      indexCrud = underlyingMongoCrud.asCrudServices[Seq[CompoundIndex]](CompoundIndex.Namespace),
      indexingWriter,
      fullReindex
    )
  }
}
