package franz.data.crud

import cats.effect.Sync
import cats.implicits._
import franz.data._
import io.circe.{Decoder, Encoder}

import scala.collection.Iterable

object CrudServicesInMemory {
  def apply[F[_] : Sync, A: Encoder : Decoder](requireContiguousVersions: Boolean) = {
    val db = MultiMap.unsafe[F, String, VersionedRecord[A]].versioned
    new CrudServicesInMemory[F, A](db, requireContiguousVersions)
  }
}

/**
 * The back-end services created which can drive a [[CrudServicesAnyCollection]] instance
 *
 * @tparam F
 */
case class CrudServicesInMemory[F[_] : Sync, A: Encoder : Decoder](db: VersionedMultiMap[F, String, VersionedRecord[A]], requireContiguousVersions: Boolean) {

  val findPrevious: ReadRecord.Service[F, (Id, Version), Option[VersionedRecord[A]]] = db.findPrevious
  val findNext: ReadRecord.Service[F, (Id, Version), Option[VersionedRecord[A]]] = db.findNext
  val findLatest: ReadRecord.Service[F, Id, Option[VersionedRecord[A]]] = db.findLatestRecord[A]
  val findVersion: ReadRecord.Service[F, (Id, Version), Option[VersionedRecord[A]]] = db.findVersionReader

  val findRecordReader: ReadRecord.Service[F, RecordCoords, Option[VersionedRecord[A]]] = ReadRecord.liftF[F, RecordCoords, Option[VersionedRecord[A]]] {
    case RecordCoords(_, id, LatestVersion) => findLatest.read(id)
    case RecordCoords(_, id, ExplicitVersion(version)) => findVersion.read(id -> version)
    case RecordCoords(_, id, PreviousVersion(version)) => findPrevious.read(id -> version)
    case RecordCoords(_, id, NextVersion(version)) => findNext.read(id -> version)
  }

  val writer: InsertRecord.Service[F, VersionedRecord[A], VersionedResponse[A]] = db.versionedWriter[A](requireContiguousVersions)

  val listService = ListRecords.liftF[F, List[String]] { range =>
    db.multiMap.list(range).map(_.keySet.toList)
  }
  val deleteService = DeleteRecord.liftF[F, Id, Option[VersionedRecord[A]]] { id =>
    db.multiMap.deleteAll(id).map {
      case None => None
      case Some(list) if list.isEmpty => None
      case Some(list) => Option(list.maxBy(_.version))
    }
  }

  val services = new CrudServices[F, A](writer, findRecordReader, listService, deleteService)
}
