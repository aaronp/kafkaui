package franz.client.pages.matches

import cats.data.{NonEmptyChain, Validated}
import cats.syntax.validated._
import franz.client.bootstrap.Component
import franz.client.js.{MessageAlerts, appClientFuture}
import franz.client.pages.Nav
import franz.data.RecordCoords
import franz.data.index.{AssociationQueries, RecordAssociations}
import io.circe.Json
import io.circe.syntax._
import scalatags.JsDom.all.{div, _}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

case class MatchesPage(nav: Nav, coords: RecordCoords, client: AssociationQueries[Future] = appClientFuture.associationsClient) extends Component {
  override val render = {
    val alerts = MessageAlerts.info()
    val matchesDiv = div().render

    client.matchEntity.read(coords).onComplete {
      case Success(associations: RecordAssociations) =>
        matchesDiv.innerHTML = ""
        matchesDiv.appendChild(AssociationsComponent(nav, associations).render())
      case Failure(err) =>
        alerts.pushMessage(s"Bang: $err")
    }

    div()(
      matchesDiv,
      alerts.messageDiv
    ).render
  }

  override def validated(): Validated[NonEmptyChain[Component.Error], Json] = true.asJson.validNec

  override def update(value: Json): Unit = {

  }
}
