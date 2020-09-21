package franz.db.impl

import com.typesafe.scalalogging.StrictLogging
import franz.data._
import franz.data.crud._
import io.circe.{Decoder, Encoder, Json}
import monix.eval.Task
import monix.reactive.Observable

import scala.reflect.ClassTag

case class CrudServicesAnyCollectionMongo(mongoVersions: VersionedRecordsMongo) extends StrictLogging {

  def list[A: ClassTag : Decoder](range: Option[QueryRange] = None): Observable[VersionedRecord[A]] = list[A](collectionNameFor[A], range)

  def list[A: ClassTag : Decoder](collectionName: CollectionName, range: Option[QueryRange]): Observable[VersionedRecord[A]] = {
    mongoVersions.latest[A](collectionName).list(range)
  }

  def listService[A: ClassTag : Decoder]: ListRecords.Service[Task, List[VersionedRecord[A]]] = ListRecords.liftF[Task, List[VersionedRecord[A]]] { range =>
    list[A](Option(range)).toListL
  }

  private def listIds[A: ClassTag : Decoder] = listService[A].mapListResults { records =>
    records.map(_.id)
  }

  def asCrudServices[A: ClassTag : Decoder : Encoder]: CrudServices[Task, A] = {
    asCrudServicesAnyCollection.asCrudServices[A](listIds[A])
  }

  def asCrudServices[A: ClassTag : Decoder : Encoder](collectionName: CollectionName): CrudServices[Task, A] = {
    asCrudServicesAnyCollection.asCrudServices[A](collectionName)(listIds[A])
  }

  def asCrudServicesAnyCollection: CrudServicesAnyCollection[Task] = {
    val insertService: InsertRecord.Service[Task, (CollectionName, VersionedJson), VersionedJsonResponse] = mongoVersions.write.insertService
    val readService: ReadRecord.Service[Task, RecordCoords, Option[VersionedJson]] = read(mongoVersions)

    new CrudServicesAnyCollection[Task](
      insertService,
      readService,
      mongoVersions.read.listService,
      mongoVersions.delete.serviceReturningRecord
    )
  }

  def read(mongoVersions: VersionedRecordsMongo): ReadRecord.Service[Task, RecordCoords, Option[VersionedJson]] = {
    ReadRecord.liftF[Task, RecordCoords, Option[VersionedJson]] { coords =>
      mongoVersions.read.get[Json](coords)
    }
  }
}
