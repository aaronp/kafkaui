package franz.db.services

import cats.syntax.option._
import com.typesafe.scalalogging.StrictLogging
import franz.data.crud._
import franz.data.index.{IndexedValue, IndexerInstance}
import franz.data.{IndexValue, VersionedRecord}
import franz.db.impl.VersionedRecordsMongo
import monix.eval.Task

/**
 * Factory to create [[IndexerInstance.Input]] from a [[VersionedRecordsMongo]]
 */
object IndexerInstanceInputMongo extends StrictLogging {

  def apply(versionedRecordsMongo: VersionedRecordsMongo): IndexerInstance.Input[Task] = {

    def readBack(id: String): Task[Option[VersionedRecord[IndexedValue]]] = {
      versionedRecordsMongo.latest[IndexedValue].first(id)
    }

    val deleteService: DeleteRecord.Service[Task, IndexValue, Option[VersionedRecord[IndexedValue]]] = {
      DeleteRecord.liftF[Task, IndexValue, Option[VersionedRecord[IndexedValue]]] { id =>
        val deleteTask = versionedRecordsMongo.delete.fromLatestCollection[IndexedValue](id) match {
          case Some(task) => task.map(_ => ())
          case None => Task.unit
        }
        readBack(id) <* deleteTask
      }
    }

    val insertService = InsertRecord.liftF[Task, VersionedRecord[IndexedValue], InsertResponse[VersionedRecord[IndexedValue]]] { input =>
      val insertTask: Task[InsertResponse[VersionedRecord[IndexedValue]]] = versionedRecordsMongo.write.insert(input).map { _ =>
        InsertResponse.inserted(input.version, input)
      }

      // mongo index errors should be treated as invalid responses so that the read/update/write loop can be retried
      insertTask.onErrorRecoverWith {
        case exp =>
          val msg = s"Index insert failed for $input: $exp"
          logger.info(msg)
          Task.pure(InvalidDetailedResponse(input.version, detail = Some(msg)))
      }
    }

    IndexerInstance.Input[Task](
      insertService,
      deleteService,
      readService = ReadRecord.liftF[Task, IndexValue, Option[VersionedRecord[IndexedValue]]](readBack),
      listService = ListRecords.liftF[Task, List[VersionedRecord[IndexedValue]]] { range => versionedRecordsMongo.latest[IndexedValue].list(range.some).toListL }
    )
  }
}
