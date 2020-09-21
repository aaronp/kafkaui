package franz.data.index

import cats.implicits._
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}


/**
 * Operations for cleaning up a string (prepending/appending, trim, upper, lower, regex replace)
 */
sealed trait StemOp {
  def apply(input: String): String
}

object StemOp {

  case class Prepend(prefix : String) extends StemOp {
    override def apply(input: String) = s"$prefix$input"
  }
  object Prepend {
    implicit val encoder = io.circe.generic.semiauto.deriveEncoder[Prepend]
    implicit val decoder = io.circe.generic.semiauto.deriveDecoder[Prepend]
  }
  case class Trim(allWhitespace: Boolean = false) extends StemOp {
    override def apply(input: String) = {
      if (allWhitespace) {
        input.filterNot(_.isWhitespace)
      } else {
        input.trim
      }
    }
  }

  object Trim {
    implicit val encoder = io.circe.generic.semiauto.deriveEncoder[Trim]
    implicit val decoder = io.circe.generic.semiauto.deriveDecoder[Trim]
  }

  case object LowerCase extends StemOp {
    override def apply(input: String) = input.toLowerCase

    val jsonValue = Json.fromString("LowerCase")
    implicit val encoder = Encoder.instance[LowerCase.type](_ => jsonValue)
    implicit val decoder: Decoder[LowerCase.type] = Decoder.decodeString.emap {
      case "LowerCase" => Right(LowerCase)
      case other => Left(s"expected 'LowerCase' but got '$other'")
    }
  }

  case object UpperCase extends StemOp {
    override def apply(input: String) = input.toUpperCase()

    val jsonValue = Json.fromString("UpperCase")
    implicit val encoder = Encoder.instance[UpperCase.type](_ => jsonValue)
    implicit val decoder: Decoder[UpperCase.type] = Decoder.decodeString.emap {
      case "UpperCase" => Right(UpperCase)
      case other => Left(s"expected 'UpperCase' but got '$other'")
    }
  }

  case object AlphaNum extends StemOp {
    override def apply(input: String) = input.filter(_.isLetterOrDigit)

    val jsonValue = Json.fromString("AlphaNum")
    implicit val encoder = Encoder.instance[AlphaNum.type](_ => jsonValue)
    implicit val decoder: Decoder[AlphaNum.type] = Decoder.decodeString.emap {
      case "AlphaNum" => Right(AlphaNum)
      case other => Left(s"expected 'AlphaNum' but got '$other'")
    }
  }

  case class TakeLeft(firstChars: Int) extends StemOp {
    override def apply(input: String) = input.take(firstChars)
  }

  object TakeLeft {
    implicit val encoder = io.circe.generic.semiauto.deriveEncoder[TakeLeft]
    implicit val decoder = io.circe.generic.semiauto.deriveDecoder[TakeLeft]
  }

  case class TakeRight(lastChars: Int) extends StemOp {
    override def apply(input: String) = input.takeRight(lastChars)
  }

  object TakeRight {
    implicit val encoder = io.circe.generic.semiauto.deriveEncoder[TakeRight]
    implicit val decoder = io.circe.generic.semiauto.deriveDecoder[TakeRight]
  }

  case class RegexReplace(regexString: String, replaceWith: String, all: Boolean = true) extends StemOp {
    private lazy val regex = regexString.r

    override def apply(input: String) = {
      if (all) {
        regex.replaceAllIn(input, replaceWith)
      } else {
        regex.replaceFirstIn(input, replaceWith)
      }
    }
  }

  object RegexReplace {
    implicit val encoder = io.circe.generic.semiauto.deriveEncoder[RegexReplace]
    implicit val decoder = io.circe.generic.semiauto.deriveDecoder[RegexReplace]
  }

  case class RegexFindFirst(regexString: String) extends StemOp {
    private lazy val regex = regexString.r

    override def apply(input: String) = regex.findFirstIn(input).getOrElse(input)
  }

  object RegexFindFirst {
    implicit val encoder = io.circe.generic.semiauto.deriveEncoder[RegexFindFirst]
    implicit val decoder = io.circe.generic.semiauto.deriveDecoder[RegexFindFirst]
  }


  implicit val encoder: Encoder[StemOp] = Encoder.instance[StemOp] {
    case msg@TakeRight(_) => msg.asJson
    case msg@TakeLeft(_) => msg.asJson
    case msg @ Trim(_) => msg.asJson
    case msg @ Prepend(_) => msg.asJson
    case msg: RegexReplace => msg.asJson
    case msg: RegexFindFirst => msg.asJson
    case AlphaNum => AlphaNum.jsonValue
    case LowerCase => LowerCase.jsonValue
    case UpperCase => UpperCase.jsonValue
  }

  implicit val decoder: Decoder[StemOp] = {
    AlphaNum.decoder.widen[StemOp]
      .or(Trim.decoder.widen[StemOp])
      .or(Prepend.decoder.widen[StemOp])
      .or(LowerCase.decoder.widen[StemOp])
      .or(UpperCase.decoder.widen[StemOp])
      .or(RegexReplace.decoder.widen[StemOp])
      .or(RegexFindFirst.decoder.widen[StemOp])
      .or(TakeRight.decoder.widen[StemOp])
      .or(TakeLeft.decoder.widen[StemOp])
  }


  /**
   * Run all the stem operations on the given input
   * @param stempOps the operations to run
   * @param input the text input
   * @return the result
   */
  def prepareString(stempOps: Iterable[StemOp], input: String): String = {
    stempOps.foldLeft(input) {
      case (str, next) => next(str)
    }
  }

}
