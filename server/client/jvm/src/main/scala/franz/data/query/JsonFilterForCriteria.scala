package franz.data.query

import donovan.json.JPath
import io.circe.syntax._
import io.circe.{Encoder, Json}

/**
 * Logic for interpreting a [[MatchCriteria]] against two json records
 */
object JsonFilterForCriteria {

  def createFilterFor[A: Encoder, B: Encoder](criteria: MatchCriteria, value: A): B => Boolean = {
    createFilter(criteria, value).compose[B](_.asJson)
  }

  def createFilter[A: Encoder](criteria: MatchCriteria, value: A): Json => Boolean = {
    apply(criteria, value.asJson, _)
  }

  def apply(criteria: MatchCriteria, lhsValue: Json, rhsValue: Json): Boolean = {
    criteria match {
      case And(lhs, rhs) => apply(lhs, lhsValue, rhsValue) && apply(rhs, lhsValue, rhsValue)
      case Or(lhs, rhs) => apply(lhs, lhsValue, rhsValue) || apply(rhs, lhsValue, rhsValue)
      case Not(c) => apply(c, lhsValue, rhsValue) == false
      case Equals(lhs, rhs) =>
        val leftV = JPath(lhs).selectValue(lhsValue)
        val rightV = JPath(rhs).selectValue(rhsValue)

        leftV == rightV
      case In(lhs, rhs, strategy) =>
        lhsInRhs(lhs, rhs, strategy, lhsValue, rhsValue)
    }
  }

  private def lhsInRhs(lhs: String, rhs: String, strategy: In.Strategy, lhsValue: Json, rhsValue: Json): Boolean = {
    val leftV: Option[Json] = JPath(lhs).selectValue(lhsValue)
    val rightV: Option[Json] = JPath(rhs).selectValue(rhsValue)


    val leftArray = leftV.flatMap(_.asArray)
    val rightArray = rightV.flatMap(_.asArray)

    (leftArray, rightArray, rightV) match {
      // ------------------------------------------------------------------------------------------------
      // 1) left value is an array, right value isn't
      // ------------------------------------------------------------------------------------------------
      case (Some(array1), None, Some(rightNonArray)) =>
        if (strategy.strict) {
          false
        } else {
          strategy match {
            case In.MatchAll(_) =>
              array1.forall(_ == rightNonArray)
            case In.MatchOne(_) =>
              array1.exists(_ == rightNonArray)
          }
        }

      // ------------------------------------------------------------------------------------------------
      // 2) left and right values are arrays
      // ------------------------------------------------------------------------------------------------
      case (Some(array1), Some(array2), _) =>
        strategy match {
          case In.MatchAll(_) => array1.forall(array2.contains)
          case In.MatchOne(_) => array1.exists(array2.contains)
        }

      // ------------------------------------------------------------------------------------------------
      // 3) left is not an array, right is an array
      // ------------------------------------------------------------------------------------------------
      case (None, Some(array2), _) => leftV.fold(false)(array2.contains)

      // ------------------------------------------------------------------------------------------------
      // 4) neither are arrays
      // ------------------------------------------------------------------------------------------------
      case (None, None, Some(rightValue)) =>
        if (strategy.strict) {
          false
        } else {
          leftV.fold(false)(_ == rightValue)
        }
      // ------------------------------------------------------------------------------------------------
      // everything else - missing fields
      // ------------------------------------------------------------------------------------------------
      case _ => false
    }
  }
}
