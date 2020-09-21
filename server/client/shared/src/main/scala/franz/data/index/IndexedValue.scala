package franz.data.index

import cats.syntax.functor._
import io.circe.{Decoder, Encoder}

/**
 * This value sits inside a [[VersionedRecord[References]]].
 * The VersionedRecord's id is the value of the json value
 *
 * Consider this json in the collection 'ourData' :
 * {{{
 *   id : thisIsOurVersionedRecordId
 *   version : 1000
 *   data : {
 *      name : foo
 *      values :  [a,b]
 *   }
 * }}}
 *
 * Then that record would result to three indexed writes:
 *
 * in collection 'index.name':
 * {{{
 *   id : foo
 *   version : xxx
 *   data : {
 *      references : [
 *        { collection : ourData, id : thisIsOurVersionedRecordId, version : 1000 }
 *      ]
 *   }
 * }}}
 *
 * and in collections "index.values"
 * {{{
 *   id : a
 *   version : xxx
 *   data : {
 *      references : [
 *        { collection : ourData, id : thisIsOurVersionedRecordId, version : 1000 }
 *      ]
 *   }
 * }}}
 * {{{
 *   id : b
 *   version : xxx
 *   data : {
 *      references : [
 *        { collection : ourData, id : thisIsOurVersionedRecordId, version : 1000 }
 *      ]
 *   }
 * }}}
 */
sealed trait IndexedValue {
  def isEmpty: Boolean

  def nonEmpty: Boolean = !isEmpty
}

/**
 * When we're adding values to a reference, at some point we'll go "this is too big to be useful - we're just inserting the whole database into a value".
 *
 * For example, if we indexed on the boolean value 'true', it wouldn't take us long to find out that finding all values which contain the value
 * "true" is damn-near useless.
 *
 * A value like a telephone number, email, or even zip code would/could be useful, however.
 *
 * @param threshold the configured threshold upon which we decided to stop indexing the references to a value
 */
case class TooManyValues(threshold: Int) extends IndexedValue {
  override def isEmpty = true
}

object TooManyValues {
  implicit val encoder = io.circe.generic.semiauto.deriveEncoder[TooManyValues]
  implicit val decoder = io.circe.generic.semiauto.deriveDecoder[TooManyValues]
}

/**
 * Represents the coordinates of collections which are assumed all to point to the same value
 *
 * @param references all the references
 */
case class FixedReferences(references: Set[ReferenceToValue]) extends IndexedValue {
  override def isEmpty = references.isEmpty

  override def toString: String = {
    references.mkString("FixedReferences:\n\t", "\n\t", "")
  }

  def remove(value: ReferenceToValue): (IndexedValue, Boolean) = {
    val contained = references.contains(value)
    val updated = references.filterNot(_.matchesKey(value))
    if (contained) {
      (FixedReferences(updated), true)
    } else {
      (this, false)
    }
  }

  /**
   * adds the reference and removes any lesser versions.
   *
   * Note: If the new 'ReferenceToValue' is for a different path and it's at a later version then we need to remove
   * the old reference.
   *
   * @param value the value to add
   * @return the new references with 'true' if values were replaced, false if it was just added
   */
  def insertOrReplace(value: ReferenceToValue, tooManyValuesThreshold: Int): (IndexedValue, Boolean) = {

    val (sameCollectionAndId, others) = references.partition(_.matchesKey(value))

    def wrap(newRefs: Set[ReferenceToValue]): IndexedValue = {
      if (newRefs.size > tooManyValuesThreshold) {
        TooManyValues(tooManyValuesThreshold)
      } else {
        FixedReferences(newRefs)
      }
    }

    if (sameCollectionAndId.nonEmpty) {
      val sameOrLaterVersions = sameCollectionAndId.filterNot(_.version < value.version)
      (wrap(sameOrLaterVersions ++ others + value), true)
    } else {
      (wrap(references + value), false)
    }
  }
}

object FixedReferences {
  implicit val encoder = io.circe.generic.semiauto.deriveEncoder[FixedReferences]
  implicit val decoder = io.circe.generic.semiauto.deriveDecoder[FixedReferences]
}

object IndexedValue {
  def apply(value: ReferenceToValue, theRest: ReferenceToValue*): IndexedValue = {
    new FixedReferences(theRest.toSet + value)
  }

  def tooManyValues(threshold: Int): IndexedValue = TooManyValues(threshold)

  import io.circe.syntax._

  implicit val encoder = Encoder.instance[IndexedValue] {
    case value@TooManyValues(_) => value.asJson
    case value@FixedReferences(_) => value.asJson
  }

  implicit val decoder: Decoder[IndexedValue] = Decoder[TooManyValues].widen.or(Decoder[FixedReferences].widen)
}

