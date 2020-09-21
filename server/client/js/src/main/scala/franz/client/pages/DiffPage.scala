package franz.client.pages

import franz.client.js.{MessageAlerts, appClientFuture}
import franz.data.diff.{Diff, RecordDiff}
import franz.data.{JsonAtPath, RecordCoords}
import org.scalajs.dom.html.Div
import scalatags.JsDom.all.{`class`, div, _}

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.util.{Failure, Success}

class DiffPage(nav: Nav, leftHandSide: RecordCoords, client: Diff.Service[Future] = appClientFuture.diffClient) {

  val alerts = MessageAlerts.info()

  val content = div(`class` := "col")(s"Diff $leftHandSide").render

  def renderDiff(diff: RecordDiff) = {
    val left: String = diff.lhs.map(JsonAtPath.asString).getOrElse("NULL")
    val right: String = diff.rhs.map(JsonAtPath.asString).getOrElse("NULL")
    div(`class` := "row")(
      div(`class` := "col-sm-2")(diff.path.mkString(".")),
      div(`class` := "col-sm-5")(span(left)),
      div(`class` := "col-sm-5")(span(right)),
    ).render
  }

  val render: Div = {
    client.diff(leftHandSide, leftHandSide.previous).onComplete {
      case Success(Some(Diff.Result(diffs))) =>
        content.innerHTML = ""
        diffs.map(renderDiff).foreach(content.appendChild)
      case Success(None) =>
        alerts.pushMessage(s"Record not found: $leftHandSide and ${leftHandSide.previous}")
      case Failure(err) =>
        alerts.pushMessage(s"Error: $err")
    }

    div(
      div(`class` := "row")(
        div(`class` := "col")(s"Diff $leftHandSide")
      ),
      content,
      alerts.messageDiv
    ).render
  }
}
