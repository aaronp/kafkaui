package franz.firestore

import java.util.Collections

import com.google.cloud.firestore.{DocumentReference, DocumentSnapshot, Transaction}
import com.typesafe.scalalogging.StrictLogging
import franz.data.crud.{InsertRecord, InsertResponse}
import franz.data.{CollectionName, VersionedJson, VersionedJsonResponse}


// we store records in:
// <collection>/<id>/latest - document
// and
// <collection>/<id>/versions - collection
object FSInsert extends StrictLogging {
  def apply(): InsertRecord.Service[FS, (CollectionName, VersionedJson), VersionedJsonResponse] = {
    instance
  }

  lazy val instance = {
    InsertRecord.liftF[FS, (CollectionName, VersionedJson), VersionedJsonResponse] {
      case (collectionName, record: VersionedJson) =>
        getFirestore.flatMap { db =>
          val recordRef = db.collection(collectionName).document(record.id)
          val versionRef: DocumentReference = recordRef.collection(Versions).document(record.version.toString)

          def blockingInsert(transaction: Transaction): Unit = {
            val json = JsonToMap(record)
            transaction.set(versionRef, json)
            transaction.set(recordRef, Collections.singletonMap("latest", json))
            logger.info(s"Wrote latest '${record.id}' to ${collectionName}.${record.id} and versioned to ${collectionName}.${record.id}.${Versions}.${record.version}")
          }

          val transaction = new Transaction.Function[VersionedJsonResponse] {
            override def updateCallback(transaction: Transaction): VersionedJsonResponse = {
              val found: DocumentSnapshot = transaction.get(versionRef).get()
              if (found.exists()) {
                logger.warn(s"ABORTING TRANSACTION, $versionRef already exists")
                InsertResponse.invalidVersion(record.version)
              } else {
                blockingInsert(transaction)
                InsertResponse.inserted(record.version, record)
              }
            }
          }

          ApiFutureTask(db.runTransaction(transaction))
        }
    }
  }
}
