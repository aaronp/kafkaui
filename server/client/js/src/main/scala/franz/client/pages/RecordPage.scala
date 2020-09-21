package franz.client.pages

import cats.implicits._
import franz.client.bootstrap.{Component, FormField}
import franz.client.js.{FutureImplicits, MessageAlerts, appClientFuture, _}
import franz.data.VersionedRecord.syntax._
import franz.data.{RecordCoords, RecordVersion}
import org.scalajs.dom.html.{Div, TextArea}
import scalatags.JsDom.all.{`class`, div, raw, _}

import scala.concurrent.ExecutionContext.Implicits._
import scala.util.{Failure, Success}

/**
 * We have a record at a particular version.
 *
 * From here we should be able to:
 * $ diff
 * $ view its schema
 * $ update
 * $ undo/load previous/next
 * $ show matches/associations
 *
 * @param nav
 * @param coords
 */
class RecordPage(nav: Nav, coords: RecordCoords) extends scalatags.LowPriorityImplicits with FutureImplicits {

  val jsonArea: TextArea = textarea(id := "inputJson", `class` := "form-control", rows := 10)("").render

  private def noSelection = jsonArea.selectionStart == jsonArea.selectionEnd

  private def cursorAtEnd = jsonArea.selectionStart == jsonArea.value.size

  jsonArea.onkeypress = e => e.onEnter {
    if (cursorAtEnd && noSelection) {
      e.cancel()
      onSave()
    }
  }

  val (alerts, errors) = {
    import scala.concurrent.duration._
    MessageAlerts.info(2.seconds) -> MessageAlerts.error(7.seconds)
  }

  private def moveToVersion(newVersion: RecordCoords): Unit = {
    appClientFuture.crudClient.readService.read(newVersion).onComplete {
      case Failure(err) =>
        errors.pushMessage(s"Error: $err")
      case Success(Some(found)) => nav.move.toRecord(coords.collection, found)
      case Success(None) =>
        errors.pushMessage(s"No version found for ${newVersion.version}")

    }
  }

  val previousVersionButton = button(`type` := "submit", `class` := "btn btn-link")("previous").render
  previousVersionButton.onclick = e => {
    e.cancel()
    moveToVersion(coords.previous)
  }

  val nextVersionButton = button(`type` := "submit", `class` := "btn btn-link")("next").render
  nextVersionButton.onclick = e => {
    e.cancel()
    moveToVersion(coords.next)
  }

  val latestVersionButton = button(`type` := "submit", `class` := "btn btn-link")("latest").render
  latestVersionButton.onclick = e => {
    e.cancel()
    moveToVersion(coords.withVersion(RecordVersion.latest))
  }

  val diffButton = button(`type` := "submit", `class` := "btn btn-primary")("Diff").render
  diffButton.onclick = e => {
    e.cancel()
    nav.move.toDiff(coords)
  }

  val associationsButton = button(`type` := "submit", `class` := "btn btn-primary")("Matches").render
  associationsButton.onclick = e => {
    nav.move.toMatches(coords)
    e.cancel()
  }

  val saveButton = button(`type` := "submit", `class` := "btn btn-primary float-left")("Save").render
  saveButton.onclick = e => {
    e.cancel()
    onSave()
  }

  def onSave() = {
    val validatedVersion = coords.versionNumOpt match {
      case None => Component.Error("Invalid version").invalidNec
      case Some(v) => v.validNec
    }
    val validatedRecord = (FormField(jsonArea).validJsonComponent, validatedVersion).mapN {
      case (d8a, version) =>
        (coords.collection, d8a.versionedRecord(id = coords.id, version = version + 1))
    }
    SaveRecord(nav, validatedRecord, errors)
  }

  def validated = FormField(jsonArea).validJson

  def nbsp = raw("&nbsp;")

  def render: Div = {
    jsonArea.focus()
    div(
      div(`class` := "form-group row")(
        previousVersionButton, nextVersionButton, latestVersionButton, nbsp, diffButton, nbsp, associationsButton
      ),
      div(`class` := "form-group row")(
        label(`for` := jsonArea.id, `class` := "control-label col-lg-1")("Data:"),
        div(`class` := "col-lg-10")(jsonArea)
      ),
      saveButton,
      div(`class` := "row")(
        alerts.messageDiv,
        errors.messageDiv)).render
  }
}
