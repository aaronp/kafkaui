package franz.firestore

import com.google.cloud.firestore.{CollectionReference, DocumentReference, Firestore, WriteResult}
import com.typesafe.scalalogging.StrictLogging
import franz.data.CollectionName
import zio.blocking.Blocking
import zio.{Has, ZIO}

import scala.jdk.CollectionConverters._

object FSDropCollection extends StrictLogging {
  def apply(collectionName: CollectionName): ZIO[Blocking with Has[Firestore], Throwable, Unit] = {
    getFirestore.flatMap { db =>
      deleteCollection(db.collection(collectionName))
    }
  }

  def deleteDoc(doc: DocumentReference): ZIO[Blocking, Throwable, WriteResult] = {
    logger.info(s"Dropping document '${doc.getPath}'")
    ZIO.foreachPar_(doc.listCollections.asScala)(deleteCollection) *> ApiFutureTask(doc.delete())
  }

  def deleteCollection(collection: CollectionReference) = {
    logger.info(s"Dropping collection '${collection.getPath}'")
    ZIO.foreachPar_(collection.listDocuments.asScala)(deleteDoc)
  }

}
