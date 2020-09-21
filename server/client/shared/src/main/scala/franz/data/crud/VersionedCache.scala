package franz.data.crud

import cats.data.State
import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.syntax.functor._
import cats.{Applicative, Functor, Monad}
import franz.data.{HasKey, IsVersioned, QueryRange, VersionedRecord}

/**
 * An in-memory service for CRUD which keeps only the latest version.
 *
 * For a full-audit version repo see the [[MultiMap]]
 *
 * @param database a mapping of keys to versioned values
 * @param keyForA
 * @param versionForA
 * @param effectFunctor
 * @tparam F
 * @tparam K
 * @tparam A
 */
class VersionedCache[F[_], K, A](database: Ref[F, Map[K, A]], requireContiguousVersions: Boolean = true)(implicit keyForA: HasKey[A, K], versionForA: IsVersioned[A], effectFunctor: Functor[F])
  extends InsertRecord[F, A, InsertResponse[A]]
    with DeleteRecord[F, K, Option[A]]
    with ReadRecord[F, K, Option[A]]
    with ListRecords[F, List[A]] {

  def insert(value: A): F[InsertResponse[A]] = database.modifyState(VersionedCache.updateState(value, requireContiguousVersions))

  def delete(key: K)  = database.modifyState(VersionedCache.removeState[K, A](key))

  def list(range : QueryRange): F[List[A]] = database.get.map(r => range.fromIterable[A](r.values).toList)

  def read(key: K): F[Option[A]] = database.get.map(_.get(key))

  /**
   *
   * @param key
   * @param f
   * @param monad
   * @return
   */
  def updateOrRemove(key: K)(f: Option[A] => Option[A])(implicit monad: Monad[F]): F[Option[InsertResponse[A]]] = {
    monad.flatMap(read(key)) { opt =>
      f(opt) match {
        case None => Applicative[F].pure(None)
        case Some(value) => insert(value).map(Option.apply)
      }
    }
  }

  override def insertService: InsertRecord.Service[F, A, InsertResponse[A]] = InsertRecord.liftF(insert)

  override def readService: ReadRecord.Service[F, K, Option[A]] = ReadRecord.liftF(read)

  override def deleteService: DeleteRecord.Service[F, K, Option[A]] = DeleteRecord.liftF(delete)

  override def listService: ListRecords.Service[F, List[A]] = ListRecords.liftF(r => list(r))
}

object VersionedCache {

  def empty[F[_] : Sync, K, A](implicit keyForA: HasKey[A, K], versionForA: IsVersioned[A]): F[VersionedCache[F, K, A]] = {
    val fRef = Ref.of[F, Map[K, A]](Map.empty[K, A])
    fRef.map { ref =>
      apply(ref)
    }
  }

  def forVersionedRecord[F[_] : Sync, A]: VersionedCache[F, String, VersionedRecord[A]] = {
    unsafe[F, String, VersionedRecord[A]]
  }

  def unsafe[F[_] : Sync, K, A](implicit keyForA: HasKey[A, K], versionForA: IsVersioned[A]): VersionedCache[F, K, A] = {
    apply(Ref.unsafe[F, Map[K, A]](Map.empty[K, A]))
  }

  def apply[F[_], K, A](tokens: Ref[F, Map[K, A]])(implicit keyForA: HasKey[A, K], versionForA: IsVersioned[A], effectFunctor: Functor[F]): VersionedCache[F, K, A] = {
    new VersionedCache[F, K, A](tokens)
  }

  def apply[F[_] : Sync : Functor, K, A]()(implicit keyForA: HasKey[A, K], versionForA: IsVersioned[A]): F[VersionedCache[F, K, A]] = {
    val emptyMap = Map[K, A]()
    import cats.syntax.functor._
    Ref.of(emptyMap).map { mapRef: Ref[F, Map[K, A]] =>
      new VersionedCache[F, K, A](mapRef)
    }
  }

  def removeState[K, A](key: K): State[Map[K, A], Option[A]] = {
    State { map: Map[K, A] =>
      map.removed(key) -> map.get(key)
    }
  }

  def updateState[K, A](data: A, requireContiguousVersions: Boolean)(implicit keyForA: HasKey[A, K], versionForA: IsVersioned[A]): State[Map[K, A], InsertResponse[A]] = {
    def nextVersionOf(a: A): Int = versionOf(a) + 1

    def versionOf(a: A): Int = versionForA.versionFor(a)

    val key = keyForA.keyFor(data)
    val suppliedVersion = versionOf(data)

    State[Map[K, A], InsertResponse[A]] { map =>
      map.get(key) match {
        case Some(old) if nextVersionOf(old) == suppliedVersion =>
          map.updated(key, data) -> InsertResponse.inserted(suppliedVersion, data)
        case Some(old) if !requireContiguousVersions && nextVersionOf(old) < suppliedVersion =>
          map.updated(key, data) -> InsertResponse.inserted(suppliedVersion, data)
        case Some(currentValue) =>
          val oldVersion = versionOf(currentValue)
          map -> InsertResponse.invalidVersion(suppliedVersion)
        case None =>
          map.updated(key, data) -> InsertResponse.inserted(suppliedVersion, data)
      }
    }
  }
}
