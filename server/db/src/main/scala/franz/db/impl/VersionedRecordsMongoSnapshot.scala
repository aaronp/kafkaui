package franz.db.impl

import franz.data.{CollectionName, VersionedRecord}
import io.circe.Json
import io.circe.generic.semiauto._
import io.circe.syntax._
import monix.eval.Task
import monix.reactive.Observable
import org.mongodb.scala.model.Filters

/**
 * A way to dump the contents of versioned records in the database
 */
object VersionedRecordsMongoSnapshot extends mongo4m.LowPriorityMongoImplicits {

  private implicit class RichNum(val i: Int) extends AnyVal {
    def plural(base: String) = {
      if (i == 1) s"$i ${base}" else s"$i ${base}s"
    }
  }

  /**
   * Restores this snapshot
   *
   * @param records
   * @param snapshot
   * @return a task which restores the snapshot
   */
  def restore(records: VersionedRecordsMongo, snapshot: CollectionSnapshot): Task[Long] = {
    val settings = if (snapshot.collectionName.endsWith("Latest")) {
      records.latestSettingsForName(snapshot.collectionName)
    } else if (snapshot.collectionName.endsWith("Versions")) {
      records.versionedSettingsForName(snapshot.collectionName)
    } else {
      records.latestSettingsForName("latestCollectionsBase")
    }

    val task = records.cache.withCollection(settings) { coll =>
      val latestWrites = snapshot.latest.recordsById.map {
        case (_, json) => coll.insertOne(json.asBsonDoc).monix
      }
      val versionedWrites = snapshot.versionSnapshot.versionedRecords.flatMap { record: RecordVersions =>
        record.versions.map {
          case (_, r: Json) => coll.insertOne(r.asBsonDoc).monix
        }
      }

      // if we have no data, then just create the collection (via a hack which creates and then deletes a record)
      // this is to ensure we get the same results in our tests which expect certain responses (e.g. listing all
      // collections)
      if (versionedWrites.isEmpty && latestWrites.isEmpty) {
        val createTask = coll.insertOne(Map("fake" -> "true").asJson.asBsonDoc).monix
        val deleteTask = coll.deleteOne(Filters.eq("fake", "true")).monix
        createTask ++ deleteTask
      } else {
        Observable.fromIterable(latestWrites ++ versionedWrites).flatten
      }
    }
    task.flatMap(_.countL)
  }

  def apply(records: VersionedRecordsMongo, verbose: Boolean): Observable[CollectionSnapshot] = {
    records.read.listCollections().flatMap { collection =>
      Observable.fromTask(snapshot(records, collection, verbose))
    }
  }

  private object BaseName {
    private val LatestR = "(.*)Latest".r
    private val VersionedR = "(.*)Versions".r

    def unapply(name: CollectionName) = {
      val base = name match {
        case LatestR(base) => base
        case VersionedR(base) => base
        case base => base
      }
      import cats.syntax.option._
      base.some
    }
  }

  /**
   * Take a snapshot from this collection
   *
   * @param records
   * @param collection
   * @param backupEntireVersionedRecord if true this returns the VersionRecord, when false it's just the .data element
   * @return
   */
  def snapshot(records: VersionedRecordsMongo, collection: CollectionName, backupEntireVersionedRecord: Boolean): Task[CollectionSnapshot] = {

    val BaseName(baseCollection) = collection

    val allLatest: Task[LatestSnapshot] = {
      records.latest[Json](baseCollection).list().toListL.map { latestRecords: List[VersionedRecord[Json]] =>
        val byid = latestRecords.groupBy(_.id).view.mapValues { allWithSameId =>
          require(allWithSameId.size == 1, allWithSameId.mkString("multiple w/ same id"))
          val head = allWithSameId.head
          if (backupEntireVersionedRecord) head.asJson else head.data
        }
        LatestSnapshot(byid.toMap)
      }
    }

    val allVersions: Task[VersionSnapshot] = {
      records.versioned[Json](baseCollection).list().toListL.map { versionedRecords: List[VersionedRecord[Json]] =>
        val list = versionedRecords.groupBy(_.id).map {
          case (id, allWithSameId) =>
            val byVersion = allWithSameId.groupBy(_.version).view.mapValues { allWithSameVersion =>
              require(allWithSameVersion.size == 1, allWithSameVersion.mkString("multiple w/ same version!"))
              val head = allWithSameVersion.head
              if (backupEntireVersionedRecord) head.asJson else head.data
            }
            RecordVersions(id, byVersion.toList)
        }
        VersionSnapshot(list.toList)
      }
    }

    Task.parZip2(allLatest, allVersions).map {
      case (latest: LatestSnapshot, ver: VersionSnapshot) =>
        val isLatest = collection.endsWith("Latest")
        val isVersioned = collection.endsWith("Versions")

        /**
         * we have to perform some name gymnastics -- stripping and appending suffixes.
         * This leads to the '*Latest' collection returning values for the *Versions collection
         */
        if (isLatest) {
          CollectionSnapshot(collection, latest, VersionSnapshot(Nil))
        } else if (isVersioned) {
          CollectionSnapshot(collection, LatestSnapshot(Map.empty), ver)
        } else {
          CollectionSnapshot(collection, latest, ver)
        }
    }
  }

  case class LatestSnapshot(recordsById: Map[String, Json]) {
    def isEmpty = recordsById.isEmpty

    def asCode: String = {
      val pears = recordsById.map {
        case (k, json) => s""" "$k" -> json\"\"\"${json.noSpaces}\"\"\" """
      }
      pears.mkString("LatestSnapshot(Map(\n", ",\n", "))")
    }

    /**
     * A way of smoothing out the actual UUIDs so we can use this in checks/tests
     *
     * @return a new snapshot w/ new IDs
     */
    def orderedIds: LatestSnapshot = {
      val newIds = recordsById.values.toList.sortBy(_.noSpaces).zipWithIndex.map {
        case (json, i) => (i.toString, json)
      }.toMap
      LatestSnapshot(newIds)
    }

    override def toString: String = {
      val rows = recordsById.toList.sortBy(_._1).map {
        case (id, json) => s"$id:${json.noSpaces}"
      }
      rows.mkString(s"${rows.size.plural("row")} \n", "\n", "")
    }
  }

  object LatestSnapshot {
    implicit val encoder = deriveEncoder[LatestSnapshot]
    implicit val decoder = deriveDecoder[LatestSnapshot]
  }


  case class RecordVersions(id: String, versions: List[(Int, Json)]) {
    def head = versions.head._2

    def asCode: String = {
      def entryAsCode(v: (Int, Json)) = {
        s"""(${v._1}, json\"\"\"${v._2.noSpaces}\"\"\" )"""
      }

      versions.map(entryAsCode).mkString(s"""RecordVersions("${id}", List(""", ", ", "))")
    }

    def decreasingVersions: RecordVersions = {
      copy(versions = versions.sortBy(_._1).reverse)
    }

    override def toString: String = {
      val rows = versions.sortBy(_._1).map {
        case (v, json) => s"$v:${json.noSpaces}"
      }
      rows.mkString(s"${id} (${rows.size.plural("version")})\n\t", "\n\t", "")
    }
  }

  object RecordVersions {
    implicit val encoder = deriveEncoder[RecordVersions]
    implicit val decoder = deriveDecoder[RecordVersions]
  }

  case class VersionSnapshot(versionedRecords: List[RecordVersions]) {
    def isEmpty: Boolean = versionedRecords.isEmpty

    def asCode: String = {
      versionedRecords.map(_.asCode).mkString("VersionSnapshot(List(\n", ",\n", "))")
    }

    def orderedIds: VersionSnapshot = {
      val latestFirst = versionedRecords.map(_.decreasingVersions)
      val newIds = latestFirst.sortBy(_.head.noSpaces).zipWithIndex.map {
        case (v, i) => v.copy(id = s"id$i")
      }
      VersionSnapshot(newIds)
    }

    override def toString: String = {
      val rows = versionedRecords.sortBy(_.id)
      rows.mkString(s"${rows.size.plural("row")}\n", "\n", "")
    }
  }

  object VersionSnapshot {
    implicit val encoder = deriveEncoder[VersionSnapshot]
    implicit val decoder = deriveDecoder[VersionSnapshot]
  }

  case class CollectionSnapshot(collectionName: CollectionName, latest: LatestSnapshot, versionSnapshot: VersionSnapshot) {
    def ordered: CollectionSnapshot = {
      copy(latest = latest.orderedIds, versionSnapshot = versionSnapshot.orderedIds)
    }

    override def toString: CollectionName = {
      val ltst = if (latest.isEmpty) "" else s"\nLATEST: $latest"
      val vsnd = if (versionSnapshot.isEmpty) "" else s"\nVERSIONS: $versionSnapshot"
      s"$collectionName$ltst$vsnd"
    }
  }

  object CollectionSnapshot {
    implicit val encoder = deriveEncoder[CollectionSnapshot]
    implicit val decoder = deriveDecoder[CollectionSnapshot]
  }

}
