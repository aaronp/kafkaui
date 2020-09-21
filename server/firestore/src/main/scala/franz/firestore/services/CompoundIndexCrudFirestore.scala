package franz.firestore.services

import com.typesafe.scalalogging.StrictLogging
import franz.data.index.CompoundIndexCrud.Writer
import franz.data.index.{CompoundIndex, CompoundIndexCrud}
import franz.data.{CollectionName, VersionedRecord}
import franz.firestore.{CrudServicesAnyCollectionFirestore, FS, FSEnv, FSList}
import io.circe.Json
import zio.stream.ZStream
import zio.interop.catz._
//import zio.interop.catz._

object CompoundIndexCrudFirestore extends StrictLogging {

  val CompoundIndexCollection = CompoundIndex.Namespace

  private def cores = Runtime.getRuntime.availableProcessors()

  // TODO - what happens if we encounter a write while we're re-indexing?
  //    task.onErrorRecoverWith {
  //      case err => ???
  //    }
  private def reindex(collection: CollectionName, writer: Writer[FS], record: VersionedRecord[Json]) = {
    logger.info(s"Reindexing $collection.${record.id}@${record.version}")
    writer.insert(collection -> record).unit
  }

  def apply(indexingWriter: Seq[CompoundIndex] => Writer[FS]): CompoundIndexCrud[FS] = {
    val underlyingCrud = CrudServicesAnyCollectionFirestore()

    def fullReindex(collection: CollectionName, indices: Seq[CompoundIndex]): FS[Unit] = {
      val reindexWriter = indexingWriter(indices)
      FSList.allRecords(CompoundIndexCollection).flatMap { records: ZStream[Any, Throwable, VersionedRecord[Json]] =>
        val newStream: ZStream[FSEnv, Throwable, Unit] = records.mapMPar(cores) { record: VersionedRecord[Json] =>
          reindex(collection, reindexWriter, record)
        }
        newStream.runDrain
      }
    }

    val crud = underlyingCrud.asCrudServices[Seq[CompoundIndex]](CompoundIndexCollection)(FSList.listIds(CompoundIndexCollection))

    CompoundIndexCrud[FS](
      indexCrud = crud,
      indexingWriter,
      fullReindex
    )
  }
}
