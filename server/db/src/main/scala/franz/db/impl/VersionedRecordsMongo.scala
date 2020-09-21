package franz.db.impl

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import franz.data.crud.{DeleteRecord, InsertRecord, InsertResponse, ListRecords}
import franz.data.{collectionNameFor, _}
import franz.db.GleichDB
import franz.db.impl.VersionedRecordsMongo.LatestResult
import io.circe.{Decoder, Encoder, Json}
import mongo4m.{CollectionSettings, LowPriorityMongoImplicits}
import monix.eval.Task
import monix.reactive.Observable
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.{Filters, Sorts}
import org.mongodb.scala.result.{DeleteResult, UpdateResult}
import org.mongodb.scala.{FindObservable, MongoDatabase, MongoWriteException}

import scala.reflect.ClassTag
import scala.util.Try

/**
 * Insert into <collection name>Version if/where the (id/version) doesn't already exist
 */
class VersionedRecordsMongo(val cache: CollectionsCache,
                            val versionedSettingsForName: CollectionName => CollectionSettings,
                            val latestSettingsForName: CollectionName => CollectionSettings
                           ) extends LowPriorityMongoImplicits with StrictLogging {
  self =>

  object delete {
    def fromLatestCollection[A: Encoder : ClassTag](id: String): Option[Task[DeleteResult]] = {
      fromLatestCollection(collectionNameFor[A], id)
    }

    def fromLatestCollection[A: Encoder](collectionName: String, id: String): Option[Task[DeleteResult]] = {
      fromCollection(latestNameFor(collectionName), id)
    }

    def serviceReturningRecord: DeleteRecord.Service[Task, (CollectionName, Id), Option[VersionedJson]] = {
      DeleteRecord.liftF[Task, (CollectionName, Id), Option[VersionedJson]] {
        case (coll, id) =>
          val latestName = latestNameFor(coll)
          fromCollection[Json](latestName, id) match {
            case None => Task.pure(None)
            case Some(deleteTask) =>
              val deleteTaskBool = deleteTask.map(_.getDeletedCount == 1)
              val readBack: Task[LatestResult[Json]] = latest[Json](coll).first(id)
              readBack <* deleteTaskBool
          }
      }
    }

    def fromCollection[A: Encoder](collectionName: String, id: String): Option[Task[DeleteResult]] = {
      val collOpt: Option[DocCollection] = cache.collectionForName(collectionName, true)
      collOpt.map { c =>
        c.deleteOne(Filters.eq(VersionedRecord.fields.Id, id)).monix.headL
      }
    }
  }

  /**
   * ===================================================================================================================
   * WRITE
   * ensure records are first written to the versioned (audit) collection and then the 'latest'. This way any query
   * for 'latest' will always have a versioned entry
   * ===================================================================================================================
   */
  object write {

    /**
     * Represents the VersionedRecordsMongo as a InsertRecord.Service
     *
     * @return
     */
    def insertService: InsertRecord.Service[Task, (CollectionName, VersionedJson), VersionedJsonResponse] = {
      InsertRecord.liftF[Task, (CollectionName, VersionedJson), VersionedJsonResponse] {
        case (collectionName, record: VersionedJson) =>
          val task: Task[VersionedJsonResponse] = insertIntoCollection(collectionName, record).map { value: Option[UpdateResult] =>
            logger.info(s"insert $collectionName returned $value")
            InsertResponse.inserted(record.version, record)
          }
          task.onErrorRecover {
            // 11000 is what was returned in test against a mongo - we should verify this against mongo docs
            // e.g. E11000 duplicate key error collection: ...... index: id_1_version_1 dup key: { : "two", : 0 }
            case err: MongoWriteException if err.getCode == 11000 => InsertResponse.invalidVersion(record.version)
          }
      }
    }

    def insert[A: Encoder : ClassTag](record: VersionedRecord[A]): Task[Option[UpdateResult]] = {
      insertIntoCollection(latestNameFor[A], versionedNameFor[A], record)
    }

    def insertIntoCollection[A: Encoder](collectionName: String, record: VersionedRecord[A]): Task[Option[UpdateResult]] = {
      // don't allow users to call their collections 'Latest' or 'Versions', 'cause that'll really confuse things
      collectionName match {
        case BaseCollection(`collectionName`) =>
          insertIntoCollection(latestNameFor(collectionName), versionedNameFor(collectionName), record)
        case BaseCollection(base) =>
          Task.raiseError(new IllegalArgumentException(s"Trying to insert into '$collectionName' which should've been '$base'"))
      }
    }

    /**
     * This inserts a record into the <latestCollectionName>Versions collection AND the <latestCollectionName>Latest collection
     *
     * The former contains a history of all versions, which we need so that we can refer to it from our associations, and the latter
     * exists so that we have a 'latest' version we can query using our [[InsertTrigger]]s
     *
     * @param latestCollectionName  the name of the <XXX>Latest collection
     * @param versionCollectionName the name of the <XXX>Versions collection
     * @param record                the record to insert
     * @tparam A
     * @return an option of the replaced record
     */
    private def insertIntoCollection[A: Encoder](latestCollectionName: String, versionCollectionName: String, record: VersionedRecord[A]): Task[Option[UpdateResult]] = {

      val versionedInsertTask = cache.withCollection(versionedSettingsForName(versionCollectionName)) { coll =>
        insertVersionedRecord(coll, record)
      }.flatten

      /** @return an option of the replaced record
       */
      val latestInsertTask: Task[Option[UpdateResult]] = {
        val settings = latestSettingsForName(latestCollectionName)
        val task = cache.withCollection(settings) { coll =>
          LatestRecordsMongo.insertLatestRecord(coll, record)
        }
        task.flatten
      }

      for {
        versionFuture <- versionedInsertTask
        _ = logger.info(s"Inserted version ${record.version} of ${record.id} into $versionCollectionName yields $versionFuture")
        updateResult <- latestInsertTask
        _ = logger.info(s"Updated version ${record.version} of ${record.id}  for $latestCollectionName")
      } yield {
        updateResult
      }
    }

    private def insertVersionedRecord[A: Encoder](coll: DocCollection, record: VersionedRecord[A]): Task[Long] = {
      logger.info(s"insertVersionedRecord(${coll.namespace.getCollectionName}, $record)")
      coll.insertOne(record).countL
    }
  }

  /**
   * A way to stream a database dump
   */
  object snapshots {

    def backupFromSnapshot(snapshot : VersionedRecordsMongoSnapshot.CollectionSnapshot): Task[Long] = {
      VersionedRecordsMongoSnapshot.restore(self, snapshot)
    }

    def apply(verbose: Boolean = false): Observable[VersionedRecordsMongoSnapshot.CollectionSnapshot] = {
      VersionedRecordsMongoSnapshot(self, verbose)
    }

    def versions[A: ClassTag](verbose: Boolean): Observable[VersionedRecordsMongoSnapshot.VersionSnapshot] = {
      versions(collectionNameFor[A], verbose)
    }

    def versions(collectionName: String, verbose: Boolean): Observable[VersionedRecordsMongoSnapshot.VersionSnapshot] = {
      apply(verbose).collect {
        case snap if snap.collectionName.startsWith(collectionName) && !snap.versionSnapshot.isEmpty => snap.versionSnapshot
      }
    }

    def latest[A: ClassTag](verbose: Boolean): Observable[VersionedRecordsMongoSnapshot.LatestSnapshot] = latest(collectionNameFor[A], verbose)

    def latest(collectionName: String, verbose: Boolean): Observable[VersionedRecordsMongoSnapshot.LatestSnapshot] = {
      apply(verbose).collect {
        case snap if snap.collectionName.startsWith(collectionName) && !snap.latest.isEmpty => snap.latest
      }
    }
  }

  /**
   * ===================================================================================================================
   * READ (QUERIES)
   * ===================================================================================================================
   */
  object read {

    def listCollections(): Observable[CollectionName] = cache.listCollections()

    /** @return a service which can list the collections
     */
    def listService: ListRecords.Service[Task, List[CollectionName]] = {
      ListRecords.liftF[Task, List[CollectionName]] { range =>
        listCollections().drop(range.from).take(range.limit).toListL.map(_.sorted)
      }
    }

    def get[A: Decoder : ClassTag](coords: RecordCoords): Task[LatestResult[A]] = {
      coords.version match {
        case LatestVersion => latest[A](coords.collection).first(coords.id)
        case PreviousVersion(previousTo) =>
          versioned[A](coords.collection).findFirst(VersionedRecordsMongo.previousVersionCriteria(coords.id, previousTo), true)
        case NextVersion(afterVersion) =>
          versioned[A](coords.collection).findFirst(VersionedRecordsMongo.nextVersionCriteria(coords.id, afterVersion), false)
        case ExplicitVersion(version) =>
          versioned[A](coords.collection).findFirst(VersionedRecordsMongo.exactVersionCriteria(coords.id, version), true)
      }
    }

    def getLatest[A: Decoder : ClassTag](id: Id): Task[LatestResult[A]] = latest[A].first(id)
  }

  def latest[A: Decoder : ClassTag]: QueryForSettings[A] = latest(collectionNameFor[A])

  def latest[A: Decoder : ClassTag](collectionName: CollectionName): QueryForSettings[A] = {
    QueryForSettings[A](latestSettingsForName(collectionName))
  }

  def versioned[A: Decoder : ClassTag]: QueryForSettings[A] = versioned(collectionNameFor[A])

  def versioned[A: Decoder : ClassTag](collectionName: CollectionName): QueryForSettings[A] = {
    QueryForSettings[A](versionedSettingsForName(collectionName))
  }

  /**
   * Queries for either the *Latest or *Versioned collection
   *
   * @tparam A the record type
   * @param settings
   */
  case class QueryForSettings[A: Decoder : ClassTag] private(settings: CollectionSettings) {
    def first(id: Id): Task[Option[VersionedRecord[A]]] = findFirst(Filters.eq(VersionedRecord.fields.Id, id), true)

    def forId(id: Id): Observable[VersionedRecord[A]] = find(Filters.eq(VersionedRecord.fields.Id, id), true)

    /**
     * Return the first value from the <collectionName>Latest collection
     *
     * @param criteria the criteria for the VersionedRecord (remember, fields are stored under data.)
     * @return the first record which matches the criteria in the *Latest collection
     */
    def findFirst(criteria: Bson, descending: Boolean): Task[Option[VersionedRecord[A]]] = {
      val result = cache.withCollection(settings) { coll: DocCollection =>
        VersionedRecordsMongo.firstVersionedRecord(coll, criteria, descending)
      }
      result.flatten
    }

    def list(range: Option[QueryRange] = None): Observable[VersionedRecord[A]] = {
      val result = cache.withCollection(settings)(c => VersionedRecordsMongo.findAllVersionedRecords[A](c, range))
      Observable.fromTask(result).flatten
    }

    def find(criteria: Bson, descending: Boolean): Observable[VersionedRecord[A]] = {
      val result = cache.withCollection(settings) { coll =>
        VersionedRecordsMongo.firstVersionedRecordForCollection(coll, criteria, descending)
      }
      Observable.fromTask(result).flatten
    }
  }

}

private[db] object VersionedRecordsMongo extends LowPriorityMongoImplicits {

  /**
   * trying to find the most recent record could (1) NOT find a record for a given ID or (2) Find the record, but fail to unmarshal it as a VersionedRecord
   *
   * @tparam A
   */
  type LatestResult[A] = Option[VersionedRecord[A]]

  type InsertResult = Option[UpdateResult]

  val VersionedCollectionName = "versionedCollectionsBase"
  val LatestCollectionName = "latestCollectionsBase"

  def previousVersionCriteria(id: String, version: Int) = {
    Filters.and(
      Filters.eq(VersionedRecord.fields.Id, id),
      Filters.lt(VersionedRecord.fields.Version, version)
    )
  }

  def nextVersionCriteria(id: String, version: Int) = {
    Filters.and(
      Filters.eq(VersionedRecord.fields.Id, id),
      Filters.gt(VersionedRecord.fields.Version, version)
    )
  }

  def exactVersionCriteria(id: String, version: Int) = {
    Filters.and(
      Filters.eq(VersionedRecord.fields.Id, id),
      Filters.eq(VersionedRecord.fields.Version, version)
    )
  }

  def firstVersionedRecord[A: Decoder : ClassTag](coll: DocCollection, criteria: Bson, descending: Boolean): Task[Option[VersionedRecord[A]]] = {
    firstVersionedRecordForCollection[A](coll, criteria, descending).headOptionL
  }

  def firstVersionedRecordsAsTry[A: Decoder : ClassTag](coll: DocCollection, criteria: Bson, descending: Boolean) = {
    val sorting = if (descending) Sorts.descending(VersionedRecord.fields.Version) else Sorts.ascending(VersionedRecord.fields.Version)
    val docs = coll.find[BsonDocument](criteria).sort(sorting)
    docs.first().monix.map(docAsVersionedA[A])
  }

  def firstVersionedRecordForCollection[A: Decoder : ClassTag](coll: DocCollection, criteria: Bson, descending: Boolean): Observable[VersionedRecord[A]] = {
    firstVersionedRecordsAsTry[A](coll, criteria, descending).flatMap(Observable.fromTry)
  }

  def findAllVersionedRecords[A: Decoder : ClassTag](coll: DocCollection, rangeOpt: Option[QueryRange] = None): Observable[VersionedRecord[A]] = {
    val query: FindObservable[BsonDocument] = coll.find[BsonDocument]().sort(Sorts.descending(VersionedRecord.fields.Version))
    withRange(query, rangeOpt).monix.map(docAsVersionedA[A]).map(_.get)
  }

  def apply(rootConfig: Config = ConfigFactory.load()): VersionedRecordsMongo = {
    val mongo: MongoDatabase = GleichDB.mongoDb(rootConfig)
    apply(CollectionsCache(mongo), rootConfig)
  }

  def apply(cache: CollectionsCache, rootConfig: Config): VersionedRecordsMongo = {
    val lookup = SettingsLookup(rootConfig)
    new VersionedRecordsMongo(cache, lookup.versionedSettingsForName, lookup.latestSettingsForName)
  }

  private def withRange(query: FindObservable[BsonDocument], rangeOpt: Option[QueryRange] = None) = {
    rangeOpt.fold(query) { range =>
      query.limit(range.limit).skip(range.from)
    }
  }

  case class SettingsLookup(rootConfig: Config) {
    val versionedSettings = CollectionSettings(rootConfig, VersionedCollectionName)
    val latestSettings = CollectionSettings(rootConfig, LatestCollectionName)

    def versionedSettingsForName(name: CollectionName): CollectionSettings = {
      Try(CollectionSettings(rootConfig, versionedNameFor(name))).getOrElse {
        versionedSettings.copy(collectionName = versionedNameFor(name))
      }
    }

    def latestSettingsForName(name: CollectionName): CollectionSettings = {
      Try(CollectionSettings(rootConfig, latestNameFor(name))).getOrElse {
        latestSettings.copy(collectionName = latestNameFor(name))
      }
    }
  }
}
