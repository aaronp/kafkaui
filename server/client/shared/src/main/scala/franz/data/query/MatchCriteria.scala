package franz.data.query

/**
 * We need these imports. I wish IntelliJ would stop "cleaning" them up:
 * {{{
 * import io.circe.generic.extras.auto._
 * import io.circe.generic.extras.Configuration
 * import io.circe.syntax._
 * }}}
 */

import io.circe.Decoder.Result
import io.circe._
import io.circe.generic.extras.Configuration
import io.circe.syntax._

import scala.reflect.ClassTag

/**
 * Representation of one of our queries
 *
 * TODO: add constant criteria (e.g. my.prop = 'fixed value'), perhaps by quoting the lhs and rhs values
 */
sealed trait MatchCriteria {
  final def or(other: MatchCriteria) = {
    val self = this
    Or(self, other)
  }

  final def not() = Not(this)

  def reverse() : MatchCriteria

  final def and(other: MatchCriteria) = {
    val self = this
    And(self, other)
  }

  def withPrefix(prefix: String): MatchCriteria = {
    mapPrefix {
      case p if !p.startsWith(prefix) => s"$prefix.$p"
    }
  }

  def withoutPrefix(prefix: String) = {
    val dotted = if (prefix.endsWith(".")) prefix else s"$prefix."
    mapPrefix {
      case p if p.startsWith(dotted) =>
        p.drop(dotted.length)
    }
  }

  def mapPrefix(prefix: PartialFunction[String, String]): MatchCriteria = mapLeftPrefix(prefix).mapRightPrefix(prefix)

  def mapLeftPrefix(prefix: PartialFunction[String, String]): MatchCriteria

  def mapRightPrefix(prefix: PartialFunction[String, String]): MatchCriteria

  final def lhsPaths: Set[String] = MatchCriteria.lhsPathsForCriteria(this)

  final def rhsPaths: Set[String] = MatchCriteria.rhsPathsForCriteria(this)

}

object MatchCriteria {

  object syntax {

    implicit class RichStr(val lhs: String) extends AnyVal {
      def equalTo(rhs: String): Equals = MatchCriteria.equals(lhs, rhs)

      def inValues(rhs: String) = MatchCriteria.in(lhs, rhs)
    }

  }

  implicit val customConfig: Configuration = Configuration.default.withSnakeCaseMemberNames.withDiscriminator("type")

  private def criteriaAsJson(mc: MatchCriteria): Json = {
    mc match {
      case Not(value) => Map("not" -> criteriaAsJson(value)).asJson
      case And(lhs, rhs) => Map("and" -> Map("lhs" -> criteriaAsJson(lhs), "rhs" -> criteriaAsJson(rhs))).asJson
      case Or(lhs, rhs) => Map("or" -> Map("lhs" -> criteriaAsJson(lhs), "rhs" -> criteriaAsJson(rhs))).asJson
      case Equals(lhs, rhs) => Map("equals" -> Map("lhs" -> Json.fromString(lhs), "rhs" -> Json.fromString(rhs))).asJson
      case In(lhs, rhs, strategy) => Json.obj("in" ->
        Map("lhs" -> Json.fromString(lhs),
          "rhs" -> Json.fromString(rhs),
          "strategy" -> In.Strategy.Codec(strategy)).asJson
      )
    }
  }

  implicit val mcEncoder: Encoder[MatchCriteria] = Encoder.instance[MatchCriteria](criteriaAsJson)

  private def decodeAnd(c: ACursor): Result[And] = {
    for {
      lhs <- criteriaFromJson(c.downField("lhs"))
      rhs <- criteriaFromJson(c.downField("rhs"))
    } yield {
      And(lhs, rhs)
    }
  }

  private def decodeOr(c: ACursor): Result[MatchCriteria] = {
    decodeAnd(c).map {
      case And(lhs, rhs) => Or(lhs, rhs)
    }
  }

  private def criteriaFromJson(c: ACursor): Result[MatchCriteria] = {

    Equals.decoder.tryDecode(c.downField("equals"))
      .orElse(In.decoder.tryDecode(c.downField("in")))
      .orElse(criteriaFromJson(c.downField("not")).map(Not.apply))
      .orElse(decodeAnd(c.downField("and")))
      .orElse(decodeOr(c.downField("or")))
  }

  implicit val mcDecoder = Decoder.instance[MatchCriteria](criteriaFromJson)

  def equals(lhs: String, rhs: String) = Equals(lhs, rhs)

  def in(lhs: String, rhs: String, strategy: In.Strategy = In.Strategy.matchOne(false)) = In(lhs, rhs, strategy)

  def lhsPathsForCriteria(criteria: MatchCriteria): Set[String] = {
    criteria match {
      case Equals(lhs, _) => Set(lhs)
      case In(lhs, _, _) => Set(lhs)
      case And(lhs, rhs) => lhsPathsForCriteria(lhs) ++ lhsPathsForCriteria(rhs)
      case Or(lhs, rhs) => lhsPathsForCriteria(lhs) ++ lhsPathsForCriteria(rhs)
      case Not(criteria) => lhsPathsForCriteria(criteria)
    }
  }

  def rhsPathsForCriteria(criteria: MatchCriteria): Set[String] = {
    criteria match {
      case Equals(_, rhs) => Set(rhs)
      case In(_, rhs, _) => Set(rhs)
      case And(lhs, rhs) => rhsPathsForCriteria(lhs) ++ rhsPathsForCriteria(rhs)
      case Or(lhs, rhs) => rhsPathsForCriteria(lhs) ++ rhsPathsForCriteria(rhs)
      case Not(criteria) => rhsPathsForCriteria(criteria)
    }
  }
}

case class Equals(lhs: String, rhs: String) extends MatchCriteria {
  override def toString: String = s"'$lhs' eq '$rhs'"

  override def reverse() = copy(lhs = rhs, rhs = lhs)

  override def mapLeftPrefix(prefix: PartialFunction[String, String]) = {
    prefix.lift.apply(lhs).fold(this) { newLhs =>
      copy(lhs = newLhs)
    }
  }

  override def mapRightPrefix(prefix: PartialFunction[String, String]) = {
    prefix.lift.apply(rhs).fold(this) { newRhs =>
      copy(rhs = newRhs)
    }
  }
}

object Equals {
  implicit val encoder = Encoder.instance[Equals] {
    case Equals(lhs, rhs) => Map("lhs" -> lhs, "rhs" -> rhs).asJson
  }

  implicit object decoder extends Decoder[Equals] {
    override def apply(c: HCursor): Result[Equals] = {
      for {
        lhs <- c.downField("lhs").as[String]
        rhs <- c.downField("rhs").as[String]
      } yield {
        Equals(lhs, rhs)
      }
    }
  }

}

/**
 * @param lhs
 * @param rhs
 */
case class In(lhs: String, rhs: String, strategy: In.Strategy) extends MatchCriteria {
  override def toString: String = s"'$lhs' in '$rhs' ${strategy}"

  // TODO - design what 'in' means for a reversed query
  override def reverse() = copy(lhs = rhs, rhs = lhs)

  override def mapLeftPrefix(prefix: PartialFunction[String, String]) = {
    prefix.lift.apply(lhs).fold(this) { newLhs =>
      copy(lhs = newLhs)
    }
  }

  override def mapRightPrefix(prefix: PartialFunction[String, String]) = {
    prefix.lift.apply(rhs).fold(this) { newRhs =>
      copy(rhs = newRhs)
    }
  }
}

object In {

  /**
   * When using 'in' matches we have to consider:
   *
   * 1) left value is an array, right value isn't
   * 2) left value is an array, right value is an array
   * 3) left is not an array, right value is an array
   * 4) left is not an array, right value is not an array
   *
   * We also have to consider what it means to match two missing entries.
   * e.g., should the lack of a field 'X' match with an empty array?
   *
   * should { x : [] } match { y : [] }?
   *
   * Each of those cases can have different meanings:
   *
   * 1) left value is an array, right value isn't
   *
   * a) if any value in the left array matches the right value that's a match
   * USE: MatchOneValue(strict = false)
   *
   * b) no-match - they're different types (array vs obj, for instance)
   * USE: MatchOneValue(strict = true) or MatchAllValues(strict = true)
   *
   * 2) left value is an array, right value is an array
   *
   * a) if any value in the left array matches any value in the right array it's a match
   * USE: MatchOneValue(strict = <not used>)
   *
   * b) all values in the left array must be in the right array
   * USE: MatchAllValues(strict = <not used>)
   *
   * *) potentially we could consider that both arrays have to match, but that would be 'equals' comparision.
   * We could also take into consideration order, but that's currently not a considered use-case
   *
   * 3) left is not an array, right value is an array
   * This is like scenario #1 in reverse
   * a) if any value in the right array matches the left value that's a match
   * USE: MatchOneValue(strict = <not used>) or MatchAllValues(strict = <not used>)
   * *) we'll ignore no-match, as this can be a typical case
   *
   * 4) left is not an array, right is not an array
   * a) no match - the 'in' clause should discard
   * USE: MatchOneValue(strict = true) or MatchAllValues(strict = true)
   * b) fall back to an equals comparison
   * USE: MatchOneValue(strict = false) or MatchAllValues(strict = false)
   *
   */
  sealed trait Strategy {
    /**
     * @return true if right type mismatches should be considered non-matches
     */
    def strict : Boolean
  }

  final case class MatchAll(override val strict: Boolean) extends Strategy
  final case class MatchOne(override val strict: Boolean) extends Strategy

  object Strategy {
    val MatchAllValuesName = "MatchAllValues"
    val MatchOneValueName = "MatchOneValue"

    def matchAll(strict: Boolean) = MatchAll(strict)
    def matchOne(strict: Boolean) = MatchOne(strict)

    implicit object Codec extends Encoder[Strategy] with Decoder[Strategy] {
      override def apply(strat: Strategy): Json = {
        strat match {
            case MatchAll(strict) =>
              Json.obj(
                "strategy" -> Json.fromString(MatchAllValuesName),
                "strict" -> Json.fromBoolean(strict)
              )
            case MatchOne(strict) => Json.obj(
              "strategy" -> Json.fromString(MatchOneValueName),
              "strict" -> Json.fromBoolean(strict)
            )
          }
      }

      override def apply(c: HCursor): Result[Strategy] = {
        c.downField("strategy").as[String] match {
          case Right(MatchAllValuesName) =>c.downField("strict").as[Boolean].map(MatchAll.apply)
          case Right(MatchOneValueName) => c.downField("strict").as[Boolean].map(MatchOne.apply)
          case Right(other) => Left(DecodingFailure(s"Invalid strategy '$other'", c.history))
          case Left(err) => Left(err)
        }
      }
    }
  }

  implicit val encoder = Encoder.instance[In] {
    case In(lhs, rhs, s) => Map(
      "lhs" -> lhs.asJson,
      "rhs" -> rhs.asJson,
      "strategy" -> s.asJson).asJson
  }

  implicit object decoder extends Decoder[In] {
    override def apply(c: HCursor): Result[In] = {
      for {
        lhs <- c.downField("lhs").as[String]
        rhs <- c.downField("rhs").as[String]
        s <- c.downField("strategy").as[Strategy]
      } yield {
        In(lhs, rhs, s)
      }
    }
  }

}

case class Or(lhs: MatchCriteria, rhs: MatchCriteria) extends MatchCriteria {
  override def toString: String = s"$lhs or $rhs"

  override def reverse() = copy(lhs.reverse(), rhs.reverse())

  override def mapLeftPrefix(prefix: PartialFunction[String, String]) = copy(lhs = lhs.mapLeftPrefix(prefix), rhs = rhs.mapLeftPrefix(prefix))

  override def mapRightPrefix(prefix: PartialFunction[String, String]) = copy(lhs = lhs.mapRightPrefix(prefix), rhs = rhs.mapRightPrefix(prefix))
}


case class Not(criteria: MatchCriteria) extends MatchCriteria {
  override def toString: String = s"!$criteria"

  override def reverse() = copy(criteria.reverse())

  override def mapLeftPrefix(prefix: PartialFunction[String, String]) = copy(criteria = criteria.mapLeftPrefix(prefix))

  override def mapRightPrefix(prefix: PartialFunction[String, String]) = copy(criteria = criteria.mapRightPrefix(prefix))
}

object Not {
  implicit val encoder: Encoder[Not] = Encoder.instance[Not] { value =>
    Json.obj("not" -> value.asJson)
  }

  implicit object decoder extends Decoder[Not] {
    override def apply(c: HCursor): Result[Not] = {
      c.downField("not").as[MatchCriteria].map { value =>
        Not(value)
      }
    }
  }

}

case class And(lhs: MatchCriteria, rhs: MatchCriteria) extends MatchCriteria {

  override def reverse() = copy(lhs.reverse(), rhs.reverse())

  override def toString: String = s"$lhs and $rhs"

  override def mapLeftPrefix(prefix: PartialFunction[String, String]) = copy(lhs = lhs.mapLeftPrefix(prefix), rhs = rhs.mapLeftPrefix(prefix))

  override def mapRightPrefix(prefix: PartialFunction[String, String]) = copy(lhs = lhs.mapRightPrefix(prefix), rhs = rhs.mapRightPrefix(prefix))
}

