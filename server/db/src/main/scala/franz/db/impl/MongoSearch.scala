package franz.db.impl

import cats.syntax.option._
import franz.data.crud.{ReadRecord, Search}
import franz.data.{RecordCoords, VersionedJson}
import io.circe.Json
import monix.eval.Task

object MongoSearch {


  // TODO - just replace both of these w/ a search impl for mongo
  def apply(mongo: VersionedRecordsMongo,
                  readService: ReadRecord.Service[Task, RecordCoords, Option[VersionedJson]]): Search.Service[Task] = {

    Search.liftF { request =>
      request.queryString.trim match {
        case "" => // list all
          val monixTask = mongo.latest[Json](request.collection).list(request.limit.some).toListL.map { found =>
            Search.Response(request.limit.from, found, found.size)
          }
          monixTask
        case criteria =>
          // TODO: the 'criteria' here is just the ID - we can do better than this
          readService.read(RecordCoords.latest(request.collection, criteria)).map {
            case None => Search.Response(0, Nil, 0)
            case Some(found: VersionedJson) => Search.Response(1, List(found), 1)
          }
      }
    }
  }
}
