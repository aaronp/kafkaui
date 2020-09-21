package franz.data.query

import scala.reflect.ClassTag
import franz.data.collectionNameFor
/**
 * @param criteria      the criteria
 * @param lhsCollection the lhs collection name
 * @param rhsCollection the rhs collection name
 */
final case class Join(criteria: MatchCriteria, lhsCollection: String, rhsCollection: String) {
  def reverse: Join = Join(criteria.reverse(), rhsCollection, lhsCollection)

  def mapCriteria(f: MatchCriteria => MatchCriteria): Join = withCriteria(f(criteria))

  def withCriteria(newCriteria: MatchCriteria): Join = copy(criteria = newCriteria)
}

object Join {
  def apply[X: ClassTag, Y: ClassTag](criteria: MatchCriteria): Join = {
    val lhsCollection = collectionNameFor[X]
    val rhsCollection = collectionNameFor[Y]
    Join(criteria, lhsCollection, rhsCollection)
  }


  implicit val encoder = io.circe.generic.semiauto.deriveEncoder[Join]
  implicit val decoder = io.circe.generic.semiauto.deriveDecoder[Join]
}
