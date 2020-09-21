package franz.db.services

import com.typesafe.scalalogging.StrictLogging
import franz.AppServices
import franz.data.crud.{CrudServices, ReadRecord}
import franz.data.index.{CompoundIndex, IndexerInstance, IndexingCrud, MatchWeights}
import franz.data.{CollectionName, RecordCoords}
import franz.db.impl.{CompoundIndexCrudMongo, CrudServicesAnyCollectionMongo, MongoSearch, VersionedRecordsMongo}
import monix.eval
import monix.eval.Task

case class MongoIndexingCrud(mongo: VersionedRecordsMongo, threshold: Int) extends StrictLogging {
  private val underlyingMongoCrud: CrudServicesAnyCollectionMongo = CrudServicesAnyCollectionMongo(mongo)
  private val underlyingCrudServices = underlyingMongoCrud.asCrudServicesAnyCollection
  val search = MongoSearch(mongo, underlyingCrudServices.readService)
  val weights = underlyingMongoCrud.asCrudServices[MatchWeights]

  // this thing can read/write/delete indices
  private val indexerService = {
    val indexerInput: IndexerInstance.Input[eval.Task] = IndexerInstanceInputMongo(mongo)
    new IndexerInstance[monix.eval.Task](indexerInput, threshold)
  }

  val indices: CrudServices[Task, Seq[CompoundIndex]] = {
    val cicm = CompoundIndexCrudMongo(mongo) { newIndices =>
      // don't read from the database - use this fixed/cached latest collection.
      // TODO - we could also just add caching to the reader below to avoid reading from mongo for *every* record
      val fixedIndices = ReadRecord.lift[Task, CollectionName, Seq[CompoundIndex]] { _ =>
        newIndices
      }
      IndexingCrud(underlyingCrudServices, indexerService, fixedIndices).indexingWriter
    }

    val original: CrudServices[Task, Seq[CompoundIndex]] = underlyingMongoCrud.asCrudServices[Seq[CompoundIndex]](CompoundIndex.Namespace)
    original.copy(insertService = cicm)
  }

  // TODO - we probably want to debounce/cache these reads
  //        val readCompoundIndices  ReadRecord.Service[T, Id, Seq[CompoundIndex]]
  val readCompoundIndices: ReadRecord.Service[Task, CollectionName, Seq[CompoundIndex]] = ReadRecord.liftF[Task, CollectionName, Seq[CompoundIndex]] { id =>
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

  def mongoAppServices: AppServices[Task] = franz.AppServices(indexingCrud, weights, indices, search)
}
