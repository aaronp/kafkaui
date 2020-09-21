package franz.client.pages.compoundindex

import cats.data.{NonEmptyChain, Validated}
import franz.client.bootstrap._
import franz.client.js.JsonAsString
import franz.data.index.StemOp
import io.circe.Json
import io.circe.syntax._

/**
 * UI controls for creating [[StemOp]] snippets
 */
object StemOpComponent {
  /**
   * @return a component which can create a list of [[StemOp]]s
   */
    def apply(): Component.Typed[List[StemOp]] = {
    def renderCell(cellJson: Json): String = {
      ChoicePanel
        .dataForJson(cellJson)
        .flatMap(_.as[StemOp].toOption)
        .map(labelForOp)
        .getOrElse(JsonAsString(cellJson))
    }

    val choices = ChoicePanel(
      ("Prepend", PrependCtrl()),
      ("Keep only alphaNumeric", constComponentFor(StemOp.AlphaNum)),
      ("Lower-Case", constComponentFor(StemOp.LowerCase)),
      ("Upper-Case", constComponentFor(StemOp.UpperCase)),
      ("Trim", TrimCtrl()),
      ("Find First Regex", FindFirstCtrl()),
      ("Replace", ReplaceFirstCtrl()),
      ("Take Right", TakeLeftCtrl()),
      ("Take Left", TakeRightCtrl())
    )

    // the inner 'choice panel' will nest its data under an index and panelData obj,
    // so we do some in/out conversions
    val converted = ListItem(choices, List("Text Operation" -> renderCell), "Text Operations", addLabel = "Add Operation").map { jsonList =>
      jsonList.asArray.fold(jsonList) { array =>
        Json.arr(array.map(ChoicePanel.dataForJsonTry): _*)
      }
    }.contramap { listOfStemOps =>
      listOfStemOps.asArray.fold(listOfStemOps) { ops =>
        val nestedArray = ops.zipWithIndex.map {
          case (j, i) => ChoicePanel.withIndex(j, i)
        }
        Json.arr(nestedArray: _*)
      }
    }
    converted.typed[List[StemOp]]
  }

  private def constComponentFor(op: StemOp) = Component.const(op.asJson)

  def labelForOp(op: StemOp): String = {
    op match {
      case StemOp.Prepend(text) => s"prepend '$text'"
      case StemOp.Trim(true) => "trim all whitespace"
      case StemOp.Trim(false) => "trim whitespace"
      case StemOp.LowerCase => "lower-case"
      case StemOp.UpperCase => "upper-case"
      case StemOp.AlphaNum => "keep only alphanumeric"
      case StemOp.TakeLeft(1) => s"keep first character"
      case StemOp.TakeLeft(n) => s"keep first $n characters"
      case StemOp.TakeRight(1) => s"keep last character"
      case StemOp.TakeRight(n) => s"keep last $n characters"
      case StemOp.RegexReplace(regexString, replaceWith, true) => s"replace all occurrences of regex $regexString with $replaceWith"
      case StemOp.RegexReplace(regexString, replaceWith, false) => s"replace first occurrence of $regexString with $replaceWith"
      case StemOp.RegexFindFirst(regex) => s"match regex $regex"
      case other => s"BUG: unknown operation $other"
    }
  }

  private case class PrependCtrl() extends Component.Base(TextInput("Text:")) {
    override def validated(): Validated[NonEmptyChain[Component.Error], Json] = {
      super.validated().map { prependJson =>
        StemOp.Prepend(JsonAsString(prependJson)).asJson
      }
    }

    override def update(value: Json): Unit = {
      value.as[StemOp.Prepend].foreach { op =>
        super.update(op.prefix.asJson)
      }
    }
  }

  private case class TrimCtrl() extends Component.Base(CheckboxInput("Trim All Whitespace:")) {
    override def validated(): Validated[NonEmptyChain[Component.Error], Json] = {
      super.validated().map { prependJson =>
        StemOp.Trim(underlying.isChecked).asJson
      }
    }

    override def update(value: Json): Unit = {
      value.as[StemOp.Trim].foreach { op =>
        underlying.check(op.allWhitespace)
      }
    }
  }

  private case class ReplaceFirstCtrl() extends Component.Base(
    InputForm("regexString" -> TextInput("Replace:", placeholderValue = "e.g. foo.*"),
      "replaceWith" -> TextInput("With:", placeholderValue = "e.g. bar"),
      "all" -> CheckboxInput("Everywhere:")
    )
  ) {

    /**
     * Convert our bullshitted InputForm json into a StemOp.RegexReplace json
     *
     * @return the value of this component as a validated
     */
    override def validated(): Validated[NonEmptyChain[Component.Error], Json] = {
      super.validated().andThen { formJson =>
        val option = for {
          obj <- formJson.asObject
          regexStringJ <- obj("regexString")
          regexString <- regexStringJ.asString
          replaceWithJ <- obj("replaceWith")
          replaceWith <- replaceWithJ.asString
          allJ <- obj("all")
          all <- allJ.asBoolean
        } yield {
          StemOp.RegexReplace(regexString, replaceWith, all).asJson
        }

        Validated.fromOption(option, NonEmptyChain.one(Component.Error(s"Couldn't unmarshal form json: ${formJson}")))
      }
    }

    /**
     * The json here is "local" - we just bullshitted it up in the InputForm we passed to our Component.Base.
     * So, when updating the input fields, we have to do a translation from the StemOp json (because that's the json we expose)
     *
     * @param value the stemOp json
     */
    override def update(value: Json): Unit = {
      value.as[StemOp.RegexReplace] match {
        case Left(_) => super.update(value)
        case Right(StemOp.RegexReplace(regex, replacement, all)) =>
          super.update(Json.obj(
            "regexString" -> regex.asJson,
            "replaceWith" -> replacement.asJson,
            "all" -> all.asJson
          ))
      }
    }
  }

  private case class FindFirstCtrl() extends Component.Base(TextInput("Match Regex:")) {
    override def validated(): Validated[NonEmptyChain[Component.Error], Json] = {
      val v = FormField(underlying.textInputControl).nonEmpty("Match Regex").map { text =>
        StemOp.RegexFindFirst(text).asJson
      }
      v.leftMap(Component.Error.apply)
    }

    override def update(value: Json): Unit = value.as[StemOp.RegexFindFirst] match {
      case Left(_) => super.update(value)
      case Right(StemOp.RegexFindFirst(regex)) => super.update(regex.asJson)
    }
  }

  private def numChars = {
    TextInput("Number of Characters:",
      initialValue = 1.toString,
      labelClass = "col-sm-4 col-form-label",
      inputDivClass = "col-sm-2")
  }

  private case class TakeLeftCtrl() extends Component.Base(numChars) {
    override def validated(): Validated[NonEmptyChain[Component.Error], Json] = {
      val v = FormField(underlying.textInputControl).numeric("Number of characters").map { n =>
        StemOp.TakeLeft(n).asJson
      }
      v.leftMap(Component.Error.apply)
    }

    override def update(value: Json): Unit = value.as[StemOp.TakeLeft] match {
      case Left(_) => super.update(value)
      case Right(StemOp.TakeLeft(n)) => super.update(n.asJson)
    }
  }

  private case class TakeRightCtrl() extends Component.Base(numChars) {
    override def validated(): Validated[NonEmptyChain[Component.Error], Json] = {
      val v = FormField(underlying.textInputControl).numeric("Number of characters").map { n =>
        StemOp.TakeRight(n).asJson
      }
      v.leftMap(Component.Error.apply)
    }

    override def update(value: Json): Unit = value.as[StemOp.TakeRight] match {
      case Left(_) => super.update(value)
      case Right(StemOp.TakeRight(n)) => super.update(n.asJson)
    }
  }

}
