package franz.data.crud

import cats.data.State
import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.syntax.functor._
import franz.data.QueryRange

class MultiMap[F[_] : Sync, K, A](val database: Ref[F, Map[K, Seq[A]]])
  extends InsertRecord[F, (K, A), Map[K, Seq[A]]]
    with DeleteRecord[F, (K, A), Option[A]]
    with ReadRecord[F, K, Seq[A]]
    with ListRecords[F, Map[K, Seq[A]]] {
  self =>

  type MultiMap = Map[K, Seq[A]]

  def versioned = new VersionedMultiMap(this)

  def read(key: K): F[Seq[A]] = readService.read(key)

  def list(range: QueryRange) = listService.list(range)

  def insert(key: K, value: A) = insertService.insert(key -> value)

  def replace[B](key: K)(f: Seq[A] => (Seq[A], B)): F[B] = {
    database.modifyState(MultiMap.replace[K, A, B](key, f))
  }

  def delete(key: K, value: A): F[Option[A]] = deleteService.delete(key -> value)

  def deleteAll(key: K): F[Option[Seq[A]]] = {
    database.modifyState(MultiMap.removeKey[K, A](key))
  }

  override def readService: ReadRecord.Service[F, K, Seq[A]] = {
    ReadRecord.liftF { key =>
      Sync[F].map(database.get)(_.getOrElse(key, Nil))
    }
  }

  def insertIfMissing(key: K, value: A): F[Map[K, Seq[A]]] = {
    val st8 = MultiMap.appendStateIfMissing[K, A](key, value)
    database.modifyState(st8)
  }

  override def listService: ListRecords.Service[F, Map[K, Seq[A]]] = {
    ListRecords.liftF(range => Sync[F].map(database.get) { db: Map[K, Seq[A]] =>
      // no idea what drop/take means for a map, but there you go
      db.drop(range.from).take(range.limit)
    })
  }

  override def deleteService: DeleteRecord.Service[F, (K, A), Option[A]] = {
    DeleteRecord.liftF[F, (K, A), Option[A]] {
      case (key, value) =>
        val delState = MultiMap.removeState[K, A](key, value)
        database.modifyState(delState)
    }
  }

  /** @return the service
   */
  override def insertService: InsertRecord.Service[F, (K, A), Map[K, Seq[A]]] = {
    InsertRecord.liftF[F, (K, A), Map[K, Seq[A]]] {
      case (k, a) =>
        val st8 = MultiMap.appendState[K, A](k, a)
        database.modifyState(st8)
    }
  }
}

object MultiMap {

  def unsafe[F[_] : Sync, K, A]: MultiMap[F, K, A] = {
    val ref: Ref[F, Map[K, Seq[A]]] = Ref.unsafe[F, Map[K, Seq[A]]](Map.empty[K, Seq[A]])
    apply(ref)
  }

  def apply[F[_] : Sync, K, A](tokens: Ref[F, Map[K, Seq[A]]]): MultiMap[F, K, A] = {
    new MultiMap[F, K, A](tokens)
  }

  def empty[F[_] : Sync, K, A](): F[MultiMap[F, K, A]] = {
    Ref.of(Map[K, Seq[A]]()).map { mapRef =>
      new MultiMap[F, K, A](mapRef)
    }
  }

  def updateState[K, A](key: K, data: A) = {
    State { map: Map[K, A] =>
      val newMap = map.updated(key, data)
      (newMap, newMap)
    }
  }

  def removeState[K, A](key: K, value: A): State[Map[K, Seq[A]], Option[A]] = {
    State { map: Map[K, Seq[A]] =>
      map.get(key) match {
        case Some(list) =>
          val (removed, newList) = list.partition(_ == value)
          if (newList.isEmpty) {
            map.removed(key) -> Option(value)
          } else {
            map.updated(key, newList) -> removed.headOption
          }
        case None => map -> None
      }
    }
  }

  def removeKey[K, A](key: K): State[Map[K, Seq[A]], Option[Seq[A]]] = {
    State { map: Map[K, Seq[A]] =>
      map.removed(key) -> map.get(key)
    }
  }

  def appendState[K, A](key: K, data: A): State[Map[K, Seq[A]], Map[K, Seq[A]]] = {
    State { map: Map[K, Seq[A]] =>
      val newList = data +: map.getOrElse(key, Nil)
      val newMap = map.updated(key, newList)
      (newMap, newMap)
    }
  }

  def appendStateIfMissing[K, A](key: K, data: A): State[Map[K, Seq[A]], Map[K, Seq[A]]] = {
    State { map: Map[K, Seq[A]] =>
      val newList = map.getOrElse(key, Nil)
      if (newList.contains(data)) {
        (map, map)
      } else {
        val newMap = map.updated(key, data +: newList)
        (newMap, newMap)
      }
    }
  }

  def replace[K, A, B](key: K, f: Seq[A] => (Seq[A], B)): State[Map[K, Seq[A]], B] = {
    State { map: Map[K, Seq[A]] =>
      val (newList, b) = f(map.getOrElse(key, Nil))
      val newMap = map.updated(key, newList)
      (newMap, b)
    }
  }
}
