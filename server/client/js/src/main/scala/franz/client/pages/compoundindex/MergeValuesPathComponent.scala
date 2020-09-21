package franz.client.pages.compoundindex

import cats.data.{NonEmptyChain, Validated}
import cats.implicits._
import franz.client.bootstrap.{Component, ModalDialog, TextInput}
import franz.data.index.MergeValues
import io.circe.Json
import io.circe.syntax._
import org.scalajs.dom.html.Div
import scalatags.JsDom.all.{div, _}

/**
 * A component for  [[MergeValues.SelectPath]]
 */
case class MergeValuesPathComponent() extends Component.Delegate {
  val fromPath = TextInput("Path:",
    placeholderValue = "e.g. user.contactDetails.name",
    labelClass = "col-lg-2 col-form-label",
    inputDivClass = "col-lg-10")

  val textInput = fromPath.textInputControl

  val stemOps = StemOpComponent()
  val modifyTextModal = ModalDialog("modify text")

  def modalDiv(): Div = modifyTextModal.render("Modify Merge Text", stemOps.render())

  private val formComponent = {
    fromPath.wrap { inputForm =>
      div()(
        inputForm,
        modifyTextModal.openButton,
        modalDiv()
      ).render
    }
  }

  val typed: Component.FlatMap[MergeValues.SelectPath] = formComponent.flatMap { _ =>
    (validPath(fromPath.currentText()), stemOps.validatedTyped()).mapN {
      case (path, ops) => MergeValues.SelectPath(path, ops)
    }
  }

  def validatedSelectPath(): Validated[NonEmptyChain[Component.Error], MergeValues.SelectPath] = typed.validatedTyped()

  underlying = typed

  override def update(inputJson: Json): Unit = {
    inputJson.as[MergeValues.SelectPath].toOption match {
      case Some(selectPath) =>
        fromPath.update(selectPath.path.mkString(".").asJson)
        stemOps.update(selectPath.stempOps.asJson)
      case None => super.update(inputJson)
    }
  }
}
