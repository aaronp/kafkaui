package franz.firestore.services

import com.typesafe.scalalogging.StrictLogging
import franz.AppServices
import franz.data.crud.{CrudServices, CrudServicesAnyCollection, ListRecords, ReadRecord}
import franz.data.index.{CompoundIndex, IndexerInstance, IndexingCrud, MatchWeights, UpdateIndexes}
import franz.data.{CollectionName, Id, RecordCoords, collectionNameFor}
import franz.firestore.{CrudServicesAnyCollectionFirestore, FS, FSList}
import zio.interop.catz._
//import zio.interop.catz._

case class FirestoreIndexingCrud(tooManyValuesThreshold: Int) extends StrictLogging {
  private val underlyingCrudServices: CrudServicesAnyCollection[FS] = CrudServicesAnyCollectionFirestore()
  val search = FirestoreSearch(underlyingCrudServices.readService)
  val weights = {
    val csacf: CrudServicesAnyCollection[FS] = CrudServicesAnyCollectionFirestore()
    val listIds: ListRecords.Service[FS, List[Id]] = FSList.listIds(collectionNameFor[MatchWeights])
    csacf.asCrudServices[MatchWeights](listIds)
  }

  // this thing can read/write/delete indices
  private val indexerService: IndexerInstance[FS] = {
    val indexerInput = IndexerInstanceInputFirestore()
    new IndexerInstance(indexerInput, tooManyValuesThreshold)
  }

  val indices: CrudServices[FS, Seq[CompoundIndex]] = {
    val cicm = CompoundIndexCrudFirestore { newIndices =>
      // don't read from the database - use this fixed/cached latest collection.
      // TODO - we could also just add caching to the reader below to avoid reading from mongo for *every* record
      val fixedIndices = ReadRecord.lift[FS, CollectionName, Seq[CompoundIndex]] { _ =>
        newIndices
      }
      IndexingCrud.apply(underlyingCrudServices, indexerService, fixedIndices).indexingWriter
    }

    val original: CrudServices[FS, Seq[CompoundIndex]] = underlyingCrudServices.asCrudServices[Seq[CompoundIndex]](CompoundIndex.Namespace)(FSList.listIds(CompoundIndex.Namespace))
    original.copy(insertService = cicm)
  }

  // TODO - we probably want to debounce/cache these reads
  //        val readCompoundIndices  ReadRecord.Service[T, Id, Seq[CompoundIndex]]
  val readCompoundIndices = ReadRecord.liftF[FS, CollectionName, Seq[CompoundIndex]] { id =>
    val coords = RecordCoords.latest(CompoundIndex.Namespace, id)
    indices.readService.read(coords).map {
      case None =>
        logger.info(s"No indices found for $coords")
        Nil
      case Some(versionedIndices) =>
        logger.info(s"$coords indices: $versionedIndices")
        versionedIndices.data
    }
  }
  val indexingCrud = IndexingCrud(underlyingCrudServices, indexerService, readCompoundIndices)

  def appServices: AppServices[FS] = franz.AppServices(indexingCrud, weights, indices, search)
}

