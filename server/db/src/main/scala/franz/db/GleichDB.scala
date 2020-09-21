package franz.db

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import franz.data.VersionedRecord
import franz.db.impl.{CollectionsCache, VersionedRecordsMongo}
import io.circe.Encoder
import mongo4m.{LowPriorityMongoImplicits, MongoConnect}
import monix.eval.Task
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.result.UpdateResult

import scala.reflect.ClassTag

class GleichDB private(versionedRecords: VersionedRecordsMongo) extends LowPriorityMongoImplicits with StrictLogging {
  def insert[A: Encoder : ClassTag](record: VersionedRecord[A]): Task[Option[UpdateResult]] = {
    versionedRecords.write.insert(record)
  }
}

/**
 * We want to write a program which performs several DB operations in parallel when a new record is inserted.
 *
 * Essentially we're taking a hit on write operations which optimize for read operations (querying schemas, deltas, associations, etc).
 *
 * A normal 'save' operation operates on a [[VersionedRecord]], which means a client would have to read an existing
 * record before updating it to the next version. Upon saving the next version, a full use-case would be to:
 *
 * $ update the *Latest collection to this [[VersionedRecord]] unless a more-recent version exists
 * $ write the [[VersionedRecord]] to a versioned collection (e.g. append-only log)
 * $ upon latest update, fetch and run and saved queries (triggers) against this collection and update *Associations with the result
 * $ calculate the delta from a previous version -- if empty, we could potentially abort this whole process
 * $ calculate and save the schema for this update in Schemas
 *
 */
object GleichDB {

  def withDatabase(newDb: String, rootConfig: Config = ConfigFactory.load()): Config = {
    import scala.jdk.CollectionConverters._
    ConfigFactory.parseMap(Map(s"mongo4m.database" -> newDb).asJava).withFallback(rootConfig)
  }

  def mongoDb(rootConfig: Config): MongoDatabase = {
    val conn = MongoConnect(rootConfig)
    conn.client.getDatabase(conn.database)
  }

  def apply(rootConfig: Config): GleichDB = apply(mongoDb(rootConfig), rootConfig)

  def apply(mongoDb: MongoDatabase, rootConfig: Config): GleichDB = {
    val cache = CollectionsCache(mongoDb)
    new GleichDB(VersionedRecordsMongo(cache, rootConfig))
  }

}
