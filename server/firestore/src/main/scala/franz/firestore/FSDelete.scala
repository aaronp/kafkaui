package franz.firestore

import cats.syntax.option._
import franz.data.crud.DeleteRecord
import franz.data.{CollectionName, Id, VersionedJson}
import zio.ZIO

object FSDelete {
  def apply(): DeleteRecord.Service[FS, (CollectionName, Id), Option[VersionedJson]] = {
    DeleteRecord.liftF[FS, (CollectionName, Id), Option[VersionedJson]] {
      case (collectionName, id) =>
        FSRead.latestDoc(collectionName, id).either.flatMap {
          case Left(_) => ZIO.succeed(None)
          case Right(found) =>
            for {
              parsed <- FSRead.parseAsVersionedJson(found)
              _ <- ApiFutureTask(found.getReference.delete())
            } yield parsed.some
        }
    }
  }
}
