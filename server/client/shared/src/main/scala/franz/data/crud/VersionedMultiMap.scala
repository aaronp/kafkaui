package franz.data.crud

import cats.Monad
import cats.effect.Sync
import cats.syntax.functor._
import franz.data.{IsVersioned, VersionedRecord, VersionedResponse}

/**
 * Functions for serching/working with versioned values
 *
 * @param multiMap
 * @tparam F
 * @tparam K
 * @tparam A
 */
final case class VersionedMultiMap[F[_] : Sync, K, A](multiMap: MultiMap[F, K, A]) {

  import multiMap._

  def find(predicate: A => Boolean): ReadRecord.Service[F, K, Option[A]] = {
    ReadRecord.liftF[F, K, Option[A]] { key =>
      read(key).map(_.find(predicate))
    }
  }

  /**
   *
   * @param requireContiguousVersions if true you have to specify 'latest version + 1'. Anything later is rejected
   * @param monad
   * @param keyIsString               evidence that 'K' is a string
   * @param recordIsVersioned         evidence that our record type 'A' is a versioned record
   * @tparam B
   * @return a writer which will only write versioned records if they are not stale/out-of-date
   */
  def versionedWriter[B](requireContiguousVersions: Boolean)(implicit monad: Monad[F], keyIsString: String =:= K, recordIsVersioned: VersionedRecord[B] =:= A): InsertRecord.Service[F, VersionedRecord[B], VersionedResponse[B]] = {
    implicit val versionedRecordEv = recordIsVersioned.flip
    val latestOpt: ReadRecord.Service[F, K, Option[A]] = findLatestRecord
    InsertRecord.liftF[F, VersionedRecord[B], VersionedResponse[B]] { record: VersionedRecord[B] =>
      val id = record.id
      val key = keyIsString(id)

      def versionOk(latestVersion: Int) = {
        (latestVersion + 1 == record.version) || (!requireContiguousVersions && latestVersion < record.version)
      }

      monad.flatMap(latestOpt.read(key)) {
        case None =>
          monad.map(insert(key, record)) { _ =>
            InsertResponse.inserted(record.version, record)
          }
        case Some(latest) if versionOk(latest.version) =>
          monad.map(insert(key, record)) { _ =>
            InsertResponse.inserted(record.version, record)
          }
        case Some(latest) =>
          monad.pure(InsertResponse.invalidVersion(record.version))
      }
    }
  }

  def findMax[B: Ordering](maxFunction: A => B): ReadRecord.Service[F, K, Option[A]] = {
    findNonEmpty(_.maxBy(maxFunction))
  }

  def findPrevious(implicit isVersioned: IsVersioned[A]): ReadRecord.Service[F, (K, Int), Option[A]] = {
    ReadRecord.liftF[F, (K, Int), Option[A]] {
      case (key, toVersion) =>
        read(key).map { seq =>
          val found = seq.foldLeft(Option.empty[(A, Int)]) {
            case (accum@Some((_, currentBestVersion)), next) =>
              val nextVersion = isVersioned.versionFor(next)
              if (nextVersion >= toVersion) {
                accum
              } else if (nextVersion > currentBestVersion) {
                Option(next -> nextVersion)
              } else {
                accum
              }
            case (None, next) if isVersioned.versionFor(next) >= toVersion => None
            case (_, next) =>
              Option(next -> isVersioned.versionFor(next))
          }
          found.map(_._1)
        }
    }
  }
  def findNext(implicit isVersioned: IsVersioned[A]): ReadRecord.Service[F, (K, Int), Option[A]] = {
    ReadRecord.liftF[F, (K, Int), Option[A]] {
      case (key, toVersion) =>
        read(key).map { seq =>
          val found = seq.foldLeft(Option.empty[(A, Int)]) {
            case (accum@Some((_, currentBestVersion)), next) =>
              val nextVersion = isVersioned.versionFor(next)
              if (nextVersion <= toVersion) {
                accum
              } else if (nextVersion < currentBestVersion) {
                Option(next -> nextVersion)
              } else {
                accum
              }
            case (None, next) if isVersioned.versionFor(next) <= toVersion => None
            case (_, next) =>
              Option(next -> isVersioned.versionFor(next))
          }
          found.map(_._1)
        }
    }
  }

  def findLatestRecord[B](implicit ev: A =:= VersionedRecord[B]): ReadRecord.Service[F, K, Option[A]] = {
    val found = findNonEmpty { seq =>
      val sorted = seq.sortBy { value: A =>
        val vr = ev(value)
        vr.version
      }
      sorted.lastOption
    }
    ReadRecord.map(found)(_.flatten)
  }

  def findLatest(implicit isVersioned: IsVersioned[A]): ReadRecord.Service[F, K, Option[A]] = {
    val found = findNonEmpty { seq =>
      val sorted = seq.sortBy(isVersioned.versionFor)
      sorted.lastOption
    }
    ReadRecord.map(found)(_.flatten)
  }

  /**
   * @param isVersioned evidence of a version
   * @return a reader which returns results for a key/version pair
   */
  def findVersionReader(implicit isVersioned: IsVersioned[A]): ReadRecord.Service[F, (K, Int), Option[A]] = {
    ReadRecord.liftF[F, (K, Int), Option[A]] {
      case (key, version) => findVersion(version).read(key)
    }
  }

  /** @param version    the version to return
   * @param isVersioned evidence that our data is versioned
   * @return a reader which will return records at a given fixed version
   */
  def findVersion(version: Int)(implicit isVersioned: IsVersioned[A]): ReadRecord.Service[F, K, Option[A]] = {
    val found = findNonEmpty { seq =>
      seq.find { a =>
        isVersioned.versionFor(a) == version
      }
    }
    ReadRecord.map(found)(_.flatten)
  }

  def findMin[B: Ordering](maxFunction: A => B): ReadRecord.Service[F, K, Option[A]] = {
    findNonEmpty(_.minBy(maxFunction))
  }

  private def findNonEmpty[B](reduce: Seq[A] => B): ReadRecord.Service[F, K, Option[B]] = {
    ReadRecord.liftF[F, K, Option[B]] { key =>
      read(key).map { seq =>
        if (seq.isEmpty) {
          None
        } else {
          Option(reduce(seq))
        }
      }
    }
  }
}
