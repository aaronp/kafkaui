package franz.data.index

import cats.Monad
import franz.data._
import franz.data.crud.{CrudServices, InsertRecord, InsertSuccess}
import franz.data.index.CompoundIndexCrud.{ReIndex, Writer}

case class CompoundIndexCrud[F[_] : Monad](indexCrud: CrudServices[F, Seq[CompoundIndex]], indexingWriter: Seq[CompoundIndex] => Writer[F], fullReindex: ReIndex[F])
  extends InsertRecord.Service[F, VersionedRecord[Seq[CompoundIndex]], VersionedResponse[Seq[CompoundIndex]]] {

  def updateIndexes(request: VersionedRecord[Seq[CompoundIndex]]): F[VersionedResponse[Seq[CompoundIndex]]] = {
    Monad[F].flatMap(indexCrud.insert(request)) {
      case insertResponse@InsertSuccess(_, after: VersionedRecord[Seq[CompoundIndex]]) =>
        Monad[F].map(fullReindex(request.id, after.data)) { _ =>
          insertResponse
        }
      case insertResponse => Monad[F].pure(insertResponse)
    }
  }

  override def insert(request: VersionedRecord[Seq[CompoundIndex]]): F[VersionedResponse[Seq[CompoundIndex]]] = updateIndexes(request)
}

/**
 * We need to find all records which don't have the new path in this collection
 *
 */
object CompoundIndexCrud {
  type Writer[F[_]] = InsertRecord.Service[F, (CollectionName, VersionedJson), VersionedJsonResponse]
  type ReIndex[F[_]] = (CollectionName, Seq[CompoundIndex]) => F[Unit]
}
