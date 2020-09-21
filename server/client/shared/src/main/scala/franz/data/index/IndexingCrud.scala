package franz.data.index

import cats.Parallel
import cats.effect.Sync
import franz.data.crud._
import franz.data._

object IndexingCrud {
  def apply[F[_] : Sync : Parallel](crudServices: CrudServicesAnyCollection[F],
                                    readIndicesForCollection: ReadRecord.Service[F, Id, Seq[CompoundIndex]],
                                    tooManyValuesThreshold: Int): IndexingCrud[F] = {
    val indicesCrud: IndexerInstance[F] = Indexer.Service[F](tooManyValuesThreshold)
    IndexingCrud(crudServices, indicesCrud, readIndicesForCollection)
  }


  def inMemory[F[_] : Sync : Parallel](requiredContiguousVersions: Boolean, tooManyValuesThreshold: Int = 100): IndexingCrud[F] = {
    val crud: CrudServicesAnyCollectionInMemory[F] = CrudServicesAnyCollectionInMemory[F](requiredContiguousVersions)

    val compoundIndices: CrudServicesInMemory[F, Seq[CompoundIndex]] = CompoundIndex.inMemorySetup[F]
    val readIndicesForCollection: ReadRecord.Service[F, Id, Seq[CompoundIndex]] = {
      ReadRecord.map(compoundIndices.findLatest) { found =>
        found.toSeq.flatMap(_.data)
      }
    }

    IndexingCrud(crud.services, readIndicesForCollection, tooManyValuesThreshold)
  }
}

/**
 * Factory for providing CRUD services which will update custom indices across collections.
 *
 * This is in a class rather than a function so that the underlying services are still accessible
 *
 * The CRUD operations will maintain columnar indices for the record values which can then be read
 * by instances of [[AssociationQueries]]
 *
 * @param crudServices             the underlying services which read/write data
 * @param indicesCrud              an indexer which can add/remove indices
 * @param readIndicesForCollection a means to read the compound indices to use for a collection
 * @tparam F
 */
case class IndexingCrud[F[_] : Sync : Parallel](crudServices: CrudServicesAnyCollection[F],
                                                indicesCrud: Indexer.Service[F],
                                                readIndicesForCollection: ReadRecord.Service[F, Id, Seq[CompoundIndex]]) {

  val indexingWriter: InsertRecord.Service[F, (CollectionName, VersionedJson), VersionedJsonResponse] = UpdateIndexes.indexedWriter(crudServices.insertService, crudServices.readService, readIndicesForCollection, indicesCrud)
  val indexingDelete = UpdateIndexes.indexedDelete(crudServices.deleteService, indicesCrud)

  // TODO - bring in weights crud for the matching

  val findMatchesForEntity: ReadRecord.Service[F, RecordCoords, RecordAssociations] = {
    val findEntity: ReadRecord.Service[F, RecordCoords, Option[VersionedJson]] = crudServices.readService

    import cats.syntax.flatMap._
    import cats.syntax.functor._

    ReadRecord.liftF { coords =>
      findEntity.read(coords).flatMap {
        case None => Sync[F].point(RecordAssociations(Nil))
        case Some(record: VersionedJson) =>
          val found: F[RecordAssociations] = indicesCrud.valueAssociations(record)

          // filter out our own references -- we don't want to match ourselves
          found.map { associations: RecordAssociations =>
            val filtered = associations.allAssociations.flatMap(_.without(coords.collection, coords.id, record.version))
            associations.copy(filtered)
          }
      }
    }
  }

  /**
   * @return CRUD services with the writer/delete services replaced with ones which update indices
   */
  def indexingCrud: CrudServicesAnyCollection[F] = {
    crudServices.copy(insertService = indexingWriter, deleteService = indexingDelete)
  }

  def associationQueries: AssociationQueries[F] = new AssociationQueries(indicesCrud, indicesCrud.matchRecordReader, findMatchesForEntity)
}
