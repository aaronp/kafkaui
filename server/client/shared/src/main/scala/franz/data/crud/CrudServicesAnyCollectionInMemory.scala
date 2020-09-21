package franz.data.crud

import cats.effect.Sync
import cats.syntax.flatMap._
import franz.data._
import io.circe.Json

/**
 * The back-end services created which can drive a [[CrudServicesAnyCollection]] instance
 *
 * @param requiredContiguousVersions
 * @tparam F
 */
case class CrudServicesAnyCollectionInMemory[F[_] : Sync](requiredContiguousVersions: Boolean) {
  val vc = VersionedCache.unsafe[F, String, VersionedJson]

  type Audit = MultiMap[F, String, VersionedJson]

  val dbByCollection = Cache.unsafe[F, CollectionName, Audit]

  private def newDB: Audit = MultiMap.unsafe[F, String, VersionedJson]

  val readLatestVersion = ReadRecord.liftF[F, (CollectionName, Id), Option[VersionedJson]] {
    case (collection, key) =>
      dbByCollection.read(collection).flatMap {
        case None => Sync[F].pure(None)
        case Some(db) => db.versioned.findLatestRecord[Json].read(key)
      }
  }

  val listDatabases = ListRecords.map(dbByCollection.listService) { found =>
    found.map(_._1)
  }

  val deleteRecord = DeleteRecord.liftF[F, (CollectionName, String), Option[VersionedJson]] {
    case (collection, key) =>
      dbByCollection.read(collection).flatMap {
        case None => Sync[F].pure(None)
        case Some(db) =>
          import cats.syntax.functor._
          db.deleteAll(key).map {
            case None => None
            case Some(Seq()) => None
            case Some(many) => Option(many.maxBy(_.version))
          }
      }
  }

  val findVersionReader = ReadRecord.liftF[F, (CollectionName, Id, Version), Option[VersionedJson]] {
    case (collection, id, version) =>
      dbByCollection.read(collection).flatMap {
        case None => Sync[F].point(None)
        case Some(db) => db.versioned.findVersion(version).read(id)
      }
  }
  val findPreviousVersionReader = ReadRecord.liftF[F, (CollectionName, Id, Version), Option[VersionedJson]] {
    case (collection, id, version) =>
      dbByCollection.read(collection).flatMap {
        case None => Sync[F].point(None)
        case Some(db) => db.versioned.findPrevious.read(id -> version)
      }
  }
  val findNextVersionReader = ReadRecord.liftF[F, (CollectionName, Id, Version), Option[VersionedJson]] {
    case (collection, id, version) =>
      dbByCollection.read(collection).flatMap {
        case None => Sync[F].point(None)
        case Some(db) => db.versioned.findNext.read(id -> version)
      }
  }

  val findRecordReader: ReadRecord.Service[F, RecordCoords, Option[VersionedJson]] = ReadRecord.liftF[F, RecordCoords, Option[VersionedJson]] {
    case RecordCoords(collection, id, LatestVersion) => readLatestVersion.read(collection, id)
    case RecordCoords(collection, id, ExplicitVersion(version)) => findVersionReader.read((collection, id, version))
    case RecordCoords(collection, id, PreviousVersion(version)) => findPreviousVersionReader.read((collection, id, version))
    case RecordCoords(collection, id, NextVersion(version)) => findNextVersionReader.read((collection, id, version))
  }

  val writer: InsertRecord.Service[F, (CollectionName, VersionedJson), VersionedJsonResponse] = InsertRecord.liftF[F, (CollectionName, VersionedJson), VersionedJsonResponse] {
    case (collection, data) =>
      dbByCollection.getOrCreate(collection, newDB).flatMap {
        case (_, databaseForCollection) =>
          databaseForCollection.versioned.versionedWriter[Json](requiredContiguousVersions).insert(data)
      }
  }

  val services = new CrudServicesAnyCollection[F](
    writer,
    findRecordReader,
    listDatabases,
    deleteRecord)
}
