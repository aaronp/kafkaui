package franz.db.impl

import com.typesafe.scalalogging.StrictLogging
import franz.data.VersionedRecord
import io.circe.{Decoder, Encoder}
import mongo4m.{BsonUtil, LowPriorityMongoImplicits}
import monix.eval.Task
import monix.reactive.Observable
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.{Filters, Sorts}
import org.mongodb.scala.result.UpdateResult

import scala.reflect.ClassTag
import scala.util.Try

/**
 * We insert into the [[VersionedRecordsMongo]], which on success updates the 'Latest' collection
 *
 */
private[db] object LatestRecordsMongo extends LowPriorityMongoImplicits with StrictLogging {

  /**
   *
   * @param coll
   * @param record
   * @tparam A
   * @return an option of the replaced record
   */
  def insertLatestRecord[A: Encoder](coll: DocCollection, record: VersionedRecord[A]): Task[Option[UpdateResult]] = {
    if (record.isFirstVersion) {
      insertFirstRecord(coll, record)
    } else {
      replaceVersionedRecord(coll, record)
    }
  }

  /**
   *
   * @param coll
   * @param record
   * @tparam A
   * @return a None as the first record does not have a matched previous one
   */
  def insertFirstRecord[A: Encoder](coll: DocCollection, record: VersionedRecord[A]): Task[Option[UpdateResult]] = {
    logger.info(s"insertFirstRecord(${coll.namespace.getCollectionName}, $record)")
    coll.insertOne(record).headL.map(_ => Option.empty[UpdateResult])
  }

  def replaceVersionedRecord[A: Encoder](coll: DocCollection, record: VersionedRecord[A]): Task[Option[UpdateResult]] = {
    val earlier: Bson = {
      Filters.and(
        Filters.lt(VersionedRecord.fields.Version, record.version),
        Filters.eq(VersionedRecord.fields.Id, record.id)
      )
    }
    logger.info(s"replaceVersionedRecord(${coll.namespace.getCollectionName}, $record), criteria is $earlier")

    replaceRecord(coll, earlier, record)
  }

  def replaceRecord[A: Encoder](coll: DocCollection, queryOfRecordToReplace: Bson, record: A): Task[Option[UpdateResult]] = {
    val doc = BsonUtil.asDocument(record)
    coll.replaceOne(queryOfRecordToReplace, doc).monix.headL.map(Option.apply)
  }

  def getLatest[A: Decoder : ClassTag](coll: DocCollection, id: String): Task[Try[VersionedRecord[A]]] = {
    val docs: Observable[BsonDocument] = coll.find[BsonDocument](Filters.eq(VersionedRecord.fields.Id, id)).sort(Sorts.descending(VersionedRecord.fields.Version)).first().monix
    docs.map(docAsVersionedA[A]).headL
  }
}
