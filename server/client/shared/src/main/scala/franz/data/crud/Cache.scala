package franz.data.crud

import cats.data.State
import cats.effect.concurrent.Ref
import cats.effect.{IO, Sync}
import franz.data.QueryRange

import scala.collection.Iterable

/**
 * A basic wrapper around a concurrent map to satisfy our CRUD service interfaces
 * @param database the in-memory map
 * @tparam F the effect type
 * @tparam K
 * @tparam A
 */
class Cache[F[_] : Sync, K, A](database: Ref[F, Map[K, A]])
  extends InsertRecord[F, (K, A), Boolean]
    with DeleteRecord[F, K, Option[A]]
    with ReadRecord[F, K, Option[A]]
    with ListRecords[F, List[(K,A)]] {

  type Cache = Map[K, A]

  def read(query: K): F[Option[A]] = readService.read(query)
  def insert(key : K, value : A) = insertService.insert(key -> value)
  def list(range : QueryRange) = listService.list(range)
  def delete(key : K) = deleteService.delete(key)

  override def readService: ReadRecord.Service[F, K, Option[A]] = {
    ReadRecord.liftF { key =>
      Sync[F].map(database.get)(_.get(key))
    }
  }
  def getOrCreate(key: K, value : => A): F[(Boolean, A)] = database.modifyState(Cache.getOrCreate(key, value))

  override def listService: ListRecords.Service[F, List[(K,A)]] = {
    ListRecords.liftF[F, List[(K,A)]] { range =>
      Sync[F].map(database.get) { r: Map[K, A] =>
        val result: Iterable[(K, A)] = range.fromIterable[(K, A)](r.toIterable)
        result.toList
      }
    }
  }

  override def deleteService: DeleteRecord.Service[F, K, Option[A]] = {
    DeleteRecord.liftF[F, K, Option[A]] { key =>
      database.modifyState(Cache.removeState[K, A](key))
    }
  }

  /** @return the service
   */
  override def insertService: InsertRecord.Service[F, (K, A), Boolean] = {
    InsertRecord.liftF[F, (K, A), Boolean] {
      case (k, a) =>
        val st8 = Cache.appendState[K, A](k, a)
        database.modifyState(st8)
    }
  }
}

object Cache {

  def unsafe[F[_] : Sync, K, A]: Cache[F, K, A] = {
    apply(Ref.unsafe[F, Map[K, A]](Map.empty[K, A]))
  }

  def apply[F[_] : Sync, K, A](tokens: Ref[F, Map[K, A]]): Cache[F, K, A] = {
    new Cache[F, K, A](tokens)
  }

  def empty[F[_] : Sync, K, A](): F[Cache[F, K, A]] = {
    import cats.syntax.functor._
    Ref.of(Map[K, A]()).map { mapRef =>
      new Cache[F, K, A](mapRef)
    }
  }

  def updateState[K, A](key: K, data: A): State[Map[K, A], Map[K, A]] = {
    State { map: Map[K, A] =>
      val newMap = map.updated(key, data)
      (newMap, newMap)
    }
  }

  def removeState[K, A](key: K): State[Map[K, A], Option[A]] = {
    State { map: Map[K, A] =>
      map.removed(key) -> map.get(key)
    }
  }

  def appendState[K, A](key: K, data: A): State[Map[K, A], Boolean] = {
    State { map: Map[K, A] =>
      val existedBefore = map.exists {
        case (k, a) => k == key && data == a
      }
      val newMap = map.updated(key, data)
      (newMap, !existedBefore)
    }
  }
  def getOrCreate[K, A](key: K, data:  => A): State[Map[K, A], (Boolean, A)] = {
    State { map: Map[K, A] =>
      map.get(key) match {
        case None =>
          val value = data
          map.updated(key, value)  -> (true, value)
        case Some(existing) => (map, (false, existing))
      }
    }
  }
}
