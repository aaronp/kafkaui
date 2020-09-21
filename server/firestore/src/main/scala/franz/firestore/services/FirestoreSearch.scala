package franz.firestore.services

import com.google.cloud.firestore.Query
import franz.data.crud.{ReadRecord, Search}
import franz.data.{RecordCoords, VersionedJson}
import franz.firestore.{FS, FSRead, getFirestore}

object FirestoreSearch {

  def apply(readService: ReadRecord.Service[FS, RecordCoords, Option[VersionedJson]]): Search.Service[FS] = {
    Search.liftF { request =>
      request.queryString.trim match {
        case "" => // list all
          getFirestore.flatMap { fs =>
            val query: Query = fs.collection(request.collection).offset(request.limit.from).limit(request.limit.limit)

            FSRead.execAllAsVersionedRecords(query).map { found =>
              Search.Response(request.limit.from, found, found.size)
            }
          }

        case criteria =>
          // TODO: the 'criteria' here is just the ID - we can do better than this
          FSRead().read(RecordCoords.latest(request.collection, criteria)).map {
            case None => Search.Response(0, Nil, 0)
            case Some(found: VersionedJson) => Search.Response(1, List(found), 1)
          }
      }
    }
  }
}
