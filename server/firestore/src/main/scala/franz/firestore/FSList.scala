package franz.firestore

import com.google.cloud.firestore.{Firestore, Query}
import franz.data.crud.ListRecords
import franz.data.{CollectionName, Id, QueryRange, VersionedRecord}
import io.circe.Json
import zio.stream.ZStream
import zio.{Has, ZIO}

object FSList {

  def listIds(collectionName: CollectionName): ListRecords.Service[FS, List[Id]] = {
    ListRecords.liftF[FS, List[Id]] { range =>
      listAll(collectionName, range).map(_.map(_.id))
    }
  }

  def listAll(collectionName: CollectionName, range: QueryRange): ZIO[Has[Firestore], Throwable, List[VersionedRecord[Json]]] = {
    getFirestore.flatMap { fs =>
      val group: Query = fs.collectionGroup(collectionName)
      FSRead.execAllAsVersionedRecords(group.limit(range.limit).offset(range.from))
    }
  }

  def allRecords(collectionName: CollectionName): ZIO[Has[Firestore], Nothing, ZStream[Any, Throwable, VersionedRecord[Json]]] = {
    getFirestore.map { fs =>
      FSRead.execAll(fs.collectionGroup(collectionName)).mapM { snapshot =>
          FSRead.parseAsVersionedJson(snapshot)
      }
    }
  }
}
