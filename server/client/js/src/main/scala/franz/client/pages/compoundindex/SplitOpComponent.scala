package franz.client.pages.compoundindex

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyChain, Validated}
import cats.syntax.option._
import cats.syntax.validated._
import franz.client.bootstrap._
import franz.client.js.{HtmlUtils, _}
import franz.client.pages.compoundindex.SplitOpComponent.{FixedCtrl, SplitTextCtrl, combineNested}
import franz.data.JsonAtPath
import franz.data.index.{SplitOp, StemOp}
import io.circe.Json
import io.circe.syntax._
import scalatags.JsDom.all.{`class`, div, h4, _}

/**
 * Page which can create SplitOps
 *
 * @param nav
 */
case class SplitOpComponent(nav: NavStack) extends Component.Delegate {
  self =>

  // if this page was created as a child of another, then this is a reference to that parent element
  //  private var parentLink: Option[NavStack.StackElement] = None
  // if then click on 'and then', we store it here
  private var andThenSplitOpt = Option.empty[NavStack.StackElement]
  private var stemOps: List[StemOp] = Nil

  val SplitAgainText = "and then..."
  val splitAgainLink = a(href := "#")(SplitAgainText).render
  splitAgainLink.onclick = e => {
    e.cancel()
    toggleAndThenSplitLink()
  }


  val stemCtrl = StemOpComponent()
  private val textControl: SplitTextCtrl = SplitTextCtrl()
  private val fixedControl: FixedCtrl = FixedCtrl()

  /**
   * turn our 'split again' link into a 'remove' link and back again
   */
  def toggleAndThenSplitLink() = {
    andThenSplitOpt match {
      case Some(elm) =>
        nav.remove(elm)
        andThenSplitOpt = None
        splitAgainLink.innerHTML = SplitAgainText
      case None =>
        val andThenPiece = SplitOpComponent(nav)
        val elm: NavStack.StackElement = nav.push("split again on...")(andThenPiece)

        //
        // wire in the event handlers for our child os that the navigation breadcrumb's link is updated when the user
        // changes the input
        //
        locally {

          andThenPiece.textControl.textInput.onkeyup = _ => {
            val t = andThenPiece.textControl.currentText()
            elm.updateLinkText(s"then split on '${t}'")
            splitAgainLink.innerHTML = s"remove split on '${t}'"
          }

          andThenPiece.fixedControl.replacementInput.onkeyup = _ => {
            val t = andThenPiece.fixedControl.currentText()
            elm.updateLinkText(s"then replace '${t}'")
            splitAgainLink.innerHTML = s"remove replace '${t}'"

          }
        }

        andThenSplitOpt = elm.some
        nav.show(elm)
        splitAgainLink.innerHTML = "remove subsequent split"
    }
  }

  val choices = ChoicePanel(
    ("Split Text", textControl),
    ("Replace Values", fixedControl)
  )
  val panel = {
    val modifyTextModal = ModalDialog("Modify text")

    modifyTextModal.openButton.onclick = e => {
      // opening modal - we can replace/refresh values here if needed
    }

    modifyTextModal.okButton.onclick = e => {
      stemCtrl.validatedTyped() match {
        case Valid(newOps: List[StemOp]) =>
          stemOps = newOps
        // we could save these here, but we seem to get this for free
        // in our outer 'validated' call
        case Invalid(error) =>
          e.cancel()
          modifyTextModal.messages.pushMessage("Invalid")
          error.iterator.map(_.message).foreach(modifyTextModal.messages.pushMessage)
      }
    }

    def instructions = p("Convert text into an array by either splitting on a character or replacing terms with a fixed array.")

    choices.wrap { inner =>
      div(`class` := "card card-default")(
        div(`class` := "card-header")(h4("Split Operations")),
        div(`class` := "card-body")(instructions, inner),
        div(`class` := "card-footer")(splitAgainLink, raw("&nbsp;"), modifyTextModal.openButton),
        modifyTextModal.render("Modify Text", stemCtrl.render())
      ).render
    }
  }

  // our component will add its children to its json
  val splitOpComponent: Component.FlatMap[SplitOp] = panel.map(ChoicePanel.dataForJsonTry).typed[SplitOp].flatMapTypedValidated {
    originalSplitOp: SplitOp =>

      // if they haven't specified split text, then just use effectively the 'identity' operation
      val splitOp = originalSplitOp match {
        case SplitOp.SplitString("") => SplitOp.Fixed.empty
        case other => other
      }

      val finalResult: Validated[NonEmptyChain[Component.Error], SplitOp] = andThenSplitOpt match {
        case Some(andNextValue) =>
          // they clicked the link, but never showed the form
          andNextValue.validatedOpt() match {
            case None => splitOp.validNec
            case Some(andThenValidated) => combineNested(splitOp, andThenValidated)
          }
        case None => splitOp.validNec
      }

      if (stemOps.nonEmpty) {
        finalResult.map { beforeStemOps =>
          beforeStemOps.andThenStem(stemOps)
        }
      } else {
        finalResult
      }
  }

  override def update(value: Json): Unit = {
    println(s"SplitOpComponent.update($value)")
    value.as[SplitOp] match {
      case Left(_) =>
        super.update(value)
      case Right(splitOp: SplitOp) =>
        stemCtrl.update(value)
        textControl.update(value)
        fixedControl.update(value)
    }
  }
  underlying = splitOpComponent
}

/** UI controls for creating [[SplitOp]] instances
 */
object SplitOpComponent {

  private def combineNested(splitOp: SplitOp, andThenValidated: Validated[NonEmptyChain[Component.Error], Json]): Validated[NonEmptyChain[Component.Error], SplitOp] = {

    andThenValidated.andThen { andThenJson =>
      andThenJson.as[SplitOp] match {
        case Left(err) =>
          Component.Error(s"the next StemOp json '${andThenJson}' couldn't be unmarshalled: ${err}").invalidNec
        case Right(next) =>
          splitOp.andThen(next).validNec
      }
    }
  }


  /**
   * @return a component which can create a list of [[SplitOp]]s
   */
  def list(nav: NavStack): Component = {
    def renderCell(cellJson: Json): String = {
      JsonAsString(cellJson)
    }

    // this shouldn't be a ListItem - it should just show '...then split again' and '...then prepare text'
    // buttons which push new nav sections for each step:
    //
    // Home / <collection name> / compound indices / <index id> / expand / split on ' ' / 1 text op / split on ',' / 3 text ops / split on '-'
    //
    ListItem(apply(nav), List("Text Operation" -> renderCell), "Text Operations", addLabel = "Add")
  }

  case class SplitTextCtrl() extends Component.Base(TextInput("On:",
    labelClass = "col-sm-2 col-form-label",
    inputDivClass = "col-sm-5"
  )) {
    def textInput = underlying.textInputControl

    def currentText() = underlying.currentText()

    override def validated(): Validated[NonEmptyChain[Component.Error], Json] = {
      super.validated().map { regex =>
        SplitOp.SplitString(JsonAsString(regex)).asJson
      }
    }

    override def update(value: Json): Unit = {
      value.as[SplitOp.SplitString].toOption match {
        case Some(split) => super.update(split.regex.asJson)
        case None => super.update(value)
      }
    }
  }


  private case class FixedInputFormData(alias: String, aliases: List[String]) {
    def asCell = aliases.sorted.mkString(s"$alias : [", ", ", "]")

    def asFixedOp: SplitOp.Fixed = SplitOp.Fixed(Map(alias -> aliases))
  }

  private object FixedInputFormData {
    implicit val encoder = io.circe.generic.semiauto.deriveEncoder[FixedInputFormData]
    implicit val decoder = io.circe.generic.semiauto.deriveDecoder[FixedInputFormData]
  }

  case class FixedCtrl() extends Component.Delegate {
    /**
     * Convert our bullshitted InputForm json into a SplitOp.Fixed json
     *
     * @return the value of this component as a validated
     */
    override def validated(): Validated[NonEmptyChain[Component.Error], Json] = {
      super.validated().andThen { formJson =>
        val option = formJson.as[FixedInputFormData].toOption.map { formData =>
          formData.asFixedOp.asJson
        }
        Validated.fromOption(option, NonEmptyChain.one(Component.Error(s"Couldn't unmarshal fixed json: ${formJson}")))
      }
    }

    override def update(value: Json): Unit = {
      value.as[SplitOp.Fixed].toOption match {
        case None => super.update(value)
        case Some(SplitOp.Fixed(map)) =>
          if (map.toList.size != 1) {
            HtmlUtils.showAlert(s"BUG: updated fixed split w/ ${map}")
          }
          val List((key, values)) = map.toList
          super.update(FixedInputFormData(key, values.toList).asJson)
      }
    }

    private val replacedByInput = TextInput("Replaced By:", placeholderValue = "the replacement text", labelClass = "col-sm-3 col-form-label")

    private val listInput = ListItem(
      inner = replacedByInput,
      tableCellsByLabel = List("Text" -> JsonAtPath.asString),
      title = "Values",
      subTitle = "A replacement value",
      addLabel = "Add Replacement",
      updateLabel = "Update Replacement"
    )

    // if our inner control hits 'enter' we should add the item
    replacedByInput.textInputControl.onkeyup = onEnter {
      listInput.onAddItem()
    }

    private val replacementComponent = TextInput("Replace:", placeholderValue = "the text to replace")
    val replacementInput = replacementComponent.textInputControl

    def currentText() = replacementComponent.currentText()

    underlying = InputForm(
      "alias" -> replacementComponent,
      "aliases" -> listInput,
    )
  }

}
