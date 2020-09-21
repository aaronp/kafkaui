package franz.client.pages

import franz.client.js._
import franz.client.js.{FutureImplicits, HtmlUtils, MessageAlerts, appClientFuture}
import franz.data._
import monix.execution.Scheduler.Implicits.global
import org.scalajs.dom.html.{Div, Input}
import scalatags.JsDom.all.{`class`, `for`, datalist, div, id, input, label, list, _}

import scala.util.{Failure, Success}

object CollectionSelect {

  trait Behavior {
    def onCollection(control: CollectionSelect, collection: CollectionName): Unit
  }

  object Behavior {
    def apply(handler: (CollectionSelect, CollectionName) => Unit) = new Behavior {
      override def onCollection(control: CollectionSelect, collection: CollectionName): Unit = {
        handler(control, collection)
      }
    }
  }

  def apply(handler: (CollectionSelect, CollectionName) => Unit): CollectionSelect = {
    new CollectionSelect(Behavior(handler))
  }

}

class CollectionSelect(behavior: CollectionSelect.Behavior) extends scalatags.LowPriorityImplicits with FutureImplicits {
  val collectionList = datalist(id := "collectionList").render
  val collectionSelect: Input = input(`class` := "form-control", id := "collectionselect", list := collectionList.id).render

  val (alerts, errors) = {
    import scala.concurrent.duration._
    MessageAlerts.info(2.seconds) -> MessageAlerts.error(7.seconds)
  }

  collectionSelect.onchange = e => {
    e.cancel()
    behavior.onCollection(this, collectionSelect.value)
  }

  collectionSelect.onkeyup = onEnterCancel {
    behavior.onCollection(this, collectionSelect.value)
  }

  def updateCollections(collections: List[CollectionName]): Unit = {
    collectionList.innerHTML = ""
    collections.foreach { name =>
      collectionList.appendChild(option(value := name).render)
    }
  }

  def refresh(queryRange: QueryRange = QueryRange(0, 1000)) = {
    appClientFuture.listCollections(queryRange).onComplete {
      case Success(allCollections) =>
        val collections: List[CollectionName] = BaseCollection.distinct(allCollections)
        updateCollections(collections)

      case Failure(err) => errors.pushMessage(s"appClientFuture.listCollections($queryRange) threw : ${err}")
    }
  }

  def render: Div = {
    div(
      collectionSelect,
      collectionList,
      alerts.messageDiv,
      errors.messageDiv
    ).render
  }

  def renderDiv: Div = {
    div(`class` := "form-group row")(
      label(`for` := collectionSelect.id, `class` := "control-label col-lg-1")("Collection:"),
      div(`class` := "col-lg-6")(collectionSelect),
      collectionList
    ).render
  }
}
