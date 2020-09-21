package franz.client.pages.compoundindex

import cats.data.{NonEmptyChain, Validated}
import cats.implicits._
import franz.client.bootstrap.{Component, InputForm, ListItem, TextInput}
import franz.client.js.{JsonAsString, onEnter}
import franz.data.index.MergeValues
import io.circe.Json
import io.circe.syntax._


/**
 * The UI component for [[MergeValues]]
 */
case class MergeValuesComponent() extends Component.Delegate {

  val targetPath = targetPathTextInput

  val separatorInput = TextInput("Separator Character:",
    initialValue = ",",
    divClass = "form-group row my-4",
    labelClass = "col-sm-3 col-form-label",
    inputDivClass = "col-lg-2")

  def renderPath(cell: Json): String = {
    cell.as[MergeValues.SelectPath].toOption match {
      case Some(merge) => merge.path.mkString(".")
      case None => JsonAsString(cell)
    }
  }

  def renderOperations(cell: Json): String = {
    cell.as[MergeValues.SelectPath].toOption match {
      case Some(merge) => merge.stempOps.map(StemOpComponent.labelForOp).mkString(" -> ")
      case None => JsonAsString(cell)
    }
  }

  val fromPathTemplate = MergeValuesPathComponent()
  val paths: ListItem = ListItem(fromPathTemplate,
    List(
      "Source Path To Merge" -> renderPath,
      "Operations" -> renderOperations
    ),
    "Input Paths",
    addLabel = "Add Source Path",
    updateLabel = "Update Source Path"
  )

  fromPathTemplate.textInput.onkeyup = onEnter {
    paths.onAddItem()
    fromPathTemplate.textInput.value = ""
  }

  private val inputForm: InputForm = InputForm(
    "paths" -> paths,
    "separator" -> separatorInput,
    "targetPath" -> targetPath
  )

  underlying = inputForm

  override def validated(): Validated[NonEmptyChain[Component.Error], Json] = {
    validatedIndex().map(_.asJson)
  }

  def validatedIndex(): Validated[NonEmptyChain[Component.Error], MergeValues] = {
    val targetPathV = validPath(targetPath.currentText())
    val pathsTyped: Component.Typed[List[MergeValues.SelectPath]] = paths.typed[List[MergeValues.SelectPath]]

    (pathsTyped.validatedTyped(), targetPathV, separatorInput.validated()).mapN {
      case (fromPaths, t, _) => MergeValues(fromPaths, t, separatorInput.currentText())
    }
  }

  override def update(value: Json): Unit = {
    value.as[MergeValues].toOption match {
      case None =>
        // if the json is e.g. NULL then each component should be reset
        paths.update(value)
        separatorInput.update(value)
        targetPath.update(value)
      case Some(mergeValues) =>
        paths.update(mergeValues.fromPaths.asJson)
        separatorInput.update(mergeValues.compoundSeparator.asJson)
        targetPath.update(mergeValues.targetPath.mkString(".").asJson)
    }
  }
}
