package franz.db.impl

import donovan.json.JPath
import franz.data._
import franz.data.query._
import io.circe.syntax._
import io.circe.{Encoder, Json}
import mongo4m.BsonUtil
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters

private[db] object MongoFilterForCriteria {

  def mongoFilterForVersionQuery[A: Encoder](join: Join, value: VersionedRecord[A]): Bson = {
    mongoFilterForVersionQuery(join, value.asJson)
  }

  def mongoFilterForVersionQuery(join: Join, value: Json): Bson = {
    val versionedCriteria = join.mapCriteria(_.withPrefix(VersionedRecord.fields.Data))
    mongoFilterForQuery(versionedCriteria, value)
  }

  def mongoFilterForQuery[A: Encoder](join: Join, value: A): Bson = {
    val paths = join.criteria.lhsPaths
    val byPath = resolveValue(paths, value.asJson)
    apply(join.criteria, byPath)
  }

  private def resolveValue(paths: Set[String], value: Json): Map[String, Option[Json]] = {
    val pears = paths.map { path =>
      path -> JPath(path).apply(value)
    }
    pears.toMap
  }

  /**
   * @param criteria     the user criteria
   * @param valuesByPath the json values by 'path' (which should be the paths referenced by the criteria)
   * @return
   */
  private[db] def apply(criteria: MatchCriteria, valuesByPath: Map[String, Option[Json]]): Bson = {
    criteria match {
      case Equals(lhs, rhs) =>
        valuesByPath.get(lhs) match {
          case None => sys.error(s"BUG: resolving the LHS paths seems to have missed '$lhs' in ${valuesByPath.keySet}")
          // there isn't a value, so we have to match on 'null' or 'empty'
          case Some(None) =>
            Filters.not(Filters.exists(rhs))
          case Some(Some(lhsValue)) =>
            if (lhsValue.isNull) {
              Filters.not(Filters.exists(rhs))
            } else {
              Filters.eq(rhs, BsonUtil.asBson(lhsValue))
            }
        }
      case In(lhs, rhs, strategy) =>
        valuesByPath.get(lhs) match {
          case None => sys.error(s"BUG: resolving the LHS paths seems to have missed '$lhs'")
          // TODO - consider strategy
          case Some(None) => Filters.not(Filters.exists(rhs))
          case Some(Some(lhsValue)) =>
            if (lhsValue.isNull) {
              Filters.not(Filters.exists(rhs))
            } else {
              Filters.in(rhs, BsonUtil.asBson(lhsValue))
            }
        }
      case Not(criteria) => Filters.not(MongoFilterForCriteria(criteria, valuesByPath))
      case And(lhs, rhs) =>
        val lhsCriteria = MongoFilterForCriteria(lhs, valuesByPath)
        val rhsCriteria = MongoFilterForCriteria(rhs, valuesByPath)
        Filters.and(lhsCriteria, rhsCriteria)
      case Or(lhs, rhs) =>
        val lhsCriteria = MongoFilterForCriteria(lhs, valuesByPath)
        val rhsCriteria = MongoFilterForCriteria(rhs, valuesByPath)

        Filters.or(lhsCriteria, rhsCriteria)
    }
  }
}
