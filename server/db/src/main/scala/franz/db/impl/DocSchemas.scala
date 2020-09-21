package franz.db.impl

import com.typesafe.config.{Config, ConfigFactory}
import franz.data.VersionedRecord.syntax._
import franz.data._
import franz.data.query.NamedSchema
import franz.db.GleichDB
import io.circe.Encoder
import mongo4m.CollectionSettings
import monix.eval.Task
import monix.reactive.Observable
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.result.UpdateResult

import scala.reflect.ClassTag

/**
 * Instead of scanning all records in a collection, we parse each record's schema as we insert it and write that down.
 *
 * Then we can:
 * 1) attach a schema version to each document to help in version migrations, etc
 * 2) easily ask the shape of the data in a collection, seeing what schemas are available
 *
 */
private[db] class DocSchemas(cache: CollectionsCache, settings: CollectionSettings) {

  def insert[A: Encoder : ClassTag](collection: String, user: String, d8a: A): Task[Option[UpdateResult]] = {
    insert(collection, user, NamedSchema[A](d8a))
  }

  def insert(collection: String, user: String, schema: NamedSchema): Task[Option[UpdateResult]] = {
    import franz.data.query.RichNamedSchema._
    val record = schema.versionedRecord(user, id = schema.md5)
    insertVersionedSchema(collection, record)
  }


  def insertVersionedSchema(collection: String, versionedRecord: VersionedRecord[NamedSchema]): Task[Option[UpdateResult]] = {
    val schemaCollection: String = schemasNameFor(collection)
    cache.withCollectionFlatten(settings.copy(collectionName = schemaCollection)) { coll =>

      // we don't keep a versioned, audit history of schemas
      LatestRecordsMongo.insertLatestRecord[NamedSchema](coll, versionedRecord)
    }
  }

  def schemasFor(collection: String, limit: Int): Observable[VersionedRecord[NamedSchema]] = {
    val schemaCollection: String = schemasNameFor(collection)
    cache.collectionForName(schemaCollection, false).fold(Observable.empty[VersionedRecord[NamedSchema]]) { coll =>
      VersionedRecordsMongo.findAllVersionedRecords[NamedSchema](coll).take(limit)
    }
  }
}

object DocSchemas {

  val CollectionName = "namedSchemasBase"

  def apply(rootConfig: Config = ConfigFactory.load()): DocSchemas = {
    val mongo: MongoDatabase = GleichDB.mongoDb(rootConfig)
    apply(CollectionsCache(mongo), rootConfig)
  }

  def apply(cache: CollectionsCache, rootConfig: Config): DocSchemas = {
    val latestSettings = CollectionSettings(rootConfig, CollectionName)
    apply(cache, latestSettings)
  }

  def apply(cache: CollectionsCache, settings: CollectionSettings): DocSchemas = {
    new DocSchemas(cache, settings)
  }
}
