package franz.client.pages

import franz.client.js._
import cats.data.{NonEmptyChain, Validated}
import franz.client.bootstrap.{Component, FormField}
import franz.client.js.MessageAlerts
import franz.data.VersionedRecord.syntax._
import franz.data.{CollectionName, VersionedRecord}
import io.circe.Json
import org.scalajs.dom.html.TextArea
import scalatags.JsDom.all.{`class`, `for`, div, id, label, rows, textarea, _}

/**
 * Means to create/edit data
 */
class CreateDataForm(val collectionName: CollectionName, nav: Nav) {

  val jsonArea: TextArea = textarea(id := "inputJson", `class` := "form-control", rows := 10)("").render
  jsonArea.onkeypress = onEnter {
      onCreateRecord()
  }
  val idInput = input(`class` := "form-control", id := "idInput", placeholder := "leave blank to generate").render
  idInput.onkeypress = onEnter {
      jsonArea.focus()
  }
  val (alerts, errors) = {
    import scala.concurrent.duration._
    MessageAlerts.info(2.seconds) -> MessageAlerts.error(7.seconds)
  }
  val saveButton = button(`type` := "submit", `class` := "btn btn-primary")("Save").render

  def onCreateRecord(): Unit = {
    SaveRecord(nav, validatedRecord, errors)
  }

  saveButton.onclick = e => {
    e.cancel()
    onCreateRecord()
  }

  def validatedRecord: Validated[NonEmptyChain[Component.Error], (CollectionName, VersionedRecord[Json])] = {
    import cats.implicits._

    val idV = FormField(idInput)
      .orElse(uniqueId)
      .noWhitespace("ID")
      .leftMap(Component.Error.apply)

    val dataV = FormField(jsonArea)
      .validJson
      .leftMap(Component.Error.apply)

    (idV, dataV).mapN {
      case (id, data) => collectionName -> data.versionedRecord(id = id)
    }
  }

  def render = {
    div(
      div(`class` := "input-group row")(
        label(`for` := idInput.id, `class` := "control-label col-lg-1")("Id:"),
        div(`class` := "col-lg-6")(idInput)
      ),
      div(`class` := "form-group row")(
        label(`for` := jsonArea.id, `class` := "control-label col-lg-1")("Data:"),
        div(`class` := "col-lg-10")(jsonArea)
      ),
      div(`class` := "row")(div(`class` := "col")(saveButton)),
      alerts.messageDiv,
      errors.messageDiv
    )
  }
}
