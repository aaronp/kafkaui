package franz.firestore.services

import cats.syntax.option._
import com.google.cloud.firestore.Firestore
import com.typesafe.scalalogging.StrictLogging
import franz.data.crud._
import franz.data.index.{IndexedValue, IndexerInstance}
import franz.data.{IndexValue, VersionedRecord, collectionNameFor}
import franz.firestore._
import io.circe.{DecodingFailure, Json}
import zio.{Has, ZIO}
import zio.interop.catz._
//import zio.interop.catz._

object IndexerInstanceInputFirestore extends StrictLogging {

  val IndexValueCollection = collectionNameFor[IndexedValue]

  def apply(): IndexerInstance.Input[FS] = {
    IndexerInstance.Input[FS](
      insertService,
      deleteService,
      readService = ReadRecord.liftF[FS, IndexValue, Option[VersionedRecord[IndexedValue]]](readBack),
      listService = ListRecords.liftF[FS, List[VersionedRecord[IndexedValue]]] { range =>
        FSList.listAll(IndexValueCollection, range).map { list =>
          list.map(_.mapAs[IndexedValue].get)
        }
      }
    )
  }

  def readBack(id: String): ZIO[Has[Firestore], DecodingFailure, Option[VersionedRecord[IndexedValue]]] = {
    FSRead.latest(IndexValueCollection, id).map {
      case None => None
      case Some(vJson) => vJson.mapAs[IndexedValue].get.some
    }
  }

  def deleteService: DeleteRecord.Service[FS, IndexValue, Option[VersionedRecord[IndexedValue]]] = {
    val deletedJson = FSDelete().deleteService.contractMapDelete[IndexValue] { id =>
      (IndexValueCollection, id)
    }
    deletedJson.mapDeleteResult {
      case None => None
      case Some(vJson) => vJson.mapAs[IndexedValue].get.some
    }
  }

  def insertService: InsertRecord.Service[FS, VersionedRecord[IndexedValue], InsertResponse[VersionedRecord[IndexedValue]]] = InsertRecord.liftF[FS, VersionedRecord[IndexedValue], InsertResponse[VersionedRecord[IndexedValue]]] { input =>
    val versionedJson: VersionedRecord[Json] = input.mapToJson
    FSInsert().insert(IndexValueCollection -> versionedJson).map { resp =>
      resp.map(_.map(_ => input.data))
    }
  }
}
