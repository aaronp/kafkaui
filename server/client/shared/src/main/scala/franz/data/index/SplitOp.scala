package franz.data.index

import cats.implicits._
import io.circe.syntax._
import io.circe.{Decoder, Encoder}

/**
 * Operations for splitting up text into values - e.g. "split on whitespace and then capitalise".
 *
 * Currently we have:
 * $ 'SplitString' - the typical 'split on whitespace' or 'separate on comma' operation to turn 'the quick brown fox' into [the, quick, brown, fox]
 * $ 'Fixed' - replace a string with some values. e.g. replace 'dave' with ['david', 'dave']
 * $ 'Prepare' - wraps an operation by performing some [[StemOp]]erations on it first (e.g. toLowerCase)
 * $ 'SplitFlatMap' - performs split operations on the results of a previous split operation (e.g. split on space, then split on comma)
 */
sealed trait SplitOp {
  def apply(input: String): Seq[String]

  def andThenStem(stemOps: Seq[StemOp]): SplitOp = SplitOp.Prepare(this, stemOps)

  def andThen(firstOp: StemOp, theRest: StemOp*): SplitOp = {
    val stemList = theRest.toList
    andThenStem(firstOp +: stemList)
  }

  def andThen(nextSplit: SplitOp): SplitOp = SplitOp.SplitFlatMap(this, nextSplit)
}

object SplitOp {

  def isValidPath(path: List[String]): Boolean = {
    // TODO - add more validation
    def invalidChar(c: Char) = {
      c.isWhitespace || c == '.'
    }

    def validSubPath(subpath: String) = {
      subpath.nonEmpty || !subpath.exists(invalidChar)
    }

    path.nonEmpty && path.forall(validSubPath)
  }

  case class SplitFlatMap(firstOp: SplitOp, andThen: SplitOp) extends SplitOp {
    override def apply(input: String): Seq[String] = firstOp(input).flatMap(andThen.apply)
  }

  object SplitFlatMap {
    implicit val encoder = io.circe.generic.semiauto.deriveEncoder[SplitFlatMap]
    implicit val decoder = io.circe.generic.semiauto.deriveDecoder[SplitFlatMap]
  }


  /**
   * run the split values via the given stem operations
   *
   * @param splitter
   * @param stemOps
   */
  case class Prepare(splitter: SplitOp, stemOps: Seq[StemOp]) extends SplitOp {
    override def apply(input: String): Seq[String] = splitter(input).map { substring =>
      StemOp.prepareString(stemOps, substring)
    }
  }

  object Prepare {
    def apply(first: StemOp, theRest: StemOp*): Prepare = {
      new Prepare(Fixed.empty, first +: theRest.toList)
    }

    implicit val encoder = io.circe.generic.semiauto.deriveEncoder[Prepare]
    implicit val decoder = io.circe.generic.semiauto.deriveDecoder[Prepare]
  }

  /**
   * Replace any values found in in the key of this map with the target values
   *
   * @param aliases
   */
  case class Fixed(aliases: Map[String, Seq[String]]) extends SplitOp {
    override def apply(input: String) = aliases.getOrElse(input, Seq(input))
  }

  object Fixed {
    def apply(tuples: (String, Seq[String])*): Fixed = new Fixed(tuples.toMap)

    def empty = new Fixed(Map.empty)

    implicit val encoder = io.circe.generic.semiauto.deriveEncoder[Fixed]
    implicit val decoder = io.circe.generic.semiauto.deriveDecoder[Fixed]
  }

  case class SplitString(regex: String) extends SplitOp {
    override def apply(input: String) = input.split(regex, -1).toSeq.map(_.trim).filterNot(_.isEmpty)
  }

  object SplitString {
    implicit val encoder = io.circe.generic.semiauto.deriveEncoder[SplitString]
    implicit val decoder = io.circe.generic.semiauto.deriveDecoder[SplitString]
  }


  implicit val encoder: Encoder[SplitOp] = Encoder.instance[SplitOp] {
    case msg@Fixed(_) => msg.asJson
    case msg@SplitString(_) => msg.asJson
    case msg@SplitFlatMap(_, _) => msg.asJson
    case msg@Prepare(_, _) => msg.asJson
  }

  implicit val decoder: Decoder[SplitOp] = {
    SplitString.decoder.widen[SplitOp]
      .or(Fixed.decoder.widen[SplitOp])
      .or(SplitFlatMap.decoder.widen[SplitOp])
      .or(Prepare.decoder.widen[SplitOp])
  }
}


