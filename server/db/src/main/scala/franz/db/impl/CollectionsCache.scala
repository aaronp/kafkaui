package franz.db.impl

import com.typesafe.scalalogging.StrictLogging
import franz.data.collectionNameFor
import mongo4m.{CollectionSettings, LowPriorityMongoImplicits}
import monix.eval.Task
import monix.reactive.Observable
import org.mongodb.scala.MongoDatabase

import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.Try

/**
 * Means of obtaining collections from mongo
 *
 * @param mongo
 */
private[db] final case class CollectionsCache(mongo: MongoDatabase) extends LowPriorityMongoImplicits with StrictLogging {
  logger.info(s"Connected to $mongo")

  private var collByName = Map[String, DocCollection]()

  private object Lock

  private def updateCache(key: String, coll: DocCollection) = Lock.synchronized {
    logger.info(s"Caching '$key' : $coll")
    collByName = collByName.updated(key, coll)
  }

  def listCollections(): Observable[String] = mongo.listCollectionNames().monix

  private def dropColl(c: DocCollection): Task[Unit] = c.drop().monix.completedL

  def drop[A: ClassTag]: Task[Unit] = {
    val name = collectionNameFor[A]
    drop(
      versionedNameFor(name),
      latestNameFor(name)
    )
  }

  def drop(collection: String, theRest: String*): Task[Unit] = {
    dropAll(collection :: theRest.toList)
  }

  def dropAll(collections: Seq[String]): Task[Unit] = {
    val tasks = collections.flatMap(n => collectionForName(n, false)).map(dropColl)
    Task.sequence(tasks).map(_ => ())
  }

  private[impl] def collectionForName(collectionName: String, fetchIfMissing: Boolean = false): Option[DocCollection] = {
    val found = collByName.get(collectionName)
    if (fetchIfMissing) {
      found.orElse {
        val opt = Try(mongo.getCollection(collectionName)).toOption
        opt.foreach(updateCache(collectionName, _))
        opt
      }
    } else {
      found
    }
  }

  def withCollectionFlatten[A](settings: CollectionSettings)(f: DocCollection => Task[A]): Task[A] = {
    withCollection(settings)(f).flatten
  }


  def withCollection[A](settings: CollectionSettings, triesRemaining: Int = 10)(f: DocCollection => A): Task[A] = {
    collectionForName(settings.collectionName) match {
      case Some(coll) => Task.pure(f(coll))
      case None =>
        logger.info(s"Trying to create collection '${settings.collectionName}'")
        settings.ensureCreated(mongo).map { coll =>
          logger.info(s"Created collection '${settings.collectionName}', updating cache")
          updateCache(settings.collectionName, coll)
          f(coll)
        }.onErrorRecoverWith {
          case err if triesRemaining > 0 =>
            logger.error(s"Error creating collection '${settings.collectionName}', $triesRemaining tries remaining : $err")
            Task.sleep(10.millis) *> withCollection(settings, triesRemaining - 1)(f)
        }
    }
  }
}
