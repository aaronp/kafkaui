package franz.client.pages.compoundindex

import franz.client.bootstrap.ListItem
import franz.client.js.{FutureImplicits, MessageAlerts, appClientFuture}
import franz.data.VersionedRecord.syntax._
import franz.data.crud.InsertSuccess
import franz.data.index.CompoundIndex
import franz.data.{CollectionName, VersionedResponse}
import scalatags.JsDom.all.{div, _}

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.util.{Failure, Success}

object CompoundIndexPage {

  // the service used by this page
  trait Service {
    def addIndexToCollection(collection: CollectionName, index: CompoundIndex): Future[VersionedResponse[Seq[CompoundIndex]]]

    def replaceIndexInCollection(collection: CollectionName, oldIndex: CompoundIndex, newIndex: CompoundIndex): Future[VersionedResponse[Seq[CompoundIndex]]]

    def removeIndexFromCollection(collection: CollectionName, index: CompoundIndex): Future[Option[VersionedResponse[Seq[CompoundIndex]]]]

    def listIndices(collection: CollectionName): Future[Seq[CompoundIndex]]
  }

  object Service {
    def apply(client: CompoundIndex.DSL[Future] = appClientFuture.compoundIndexClient) = new Service {
      override def addIndexToCollection(collection: CollectionName, index: CompoundIndex) = {
        client.addIndexToCollection(collection, index)
      }

      override def replaceIndexInCollection(collection: CollectionName, oldIndex: CompoundIndex, newIndex: CompoundIndex): Future[VersionedResponse[Seq[CompoundIndex]]] = {
        client.replaceIndexInCollection(collection, oldIndex, newIndex)
      }

      override def removeIndexFromCollection(collection: CollectionName, index: CompoundIndex): Future[Option[VersionedResponse[Seq[CompoundIndex]]]] = {
        client.removeIndexFromCollection(collection, index)
      }

      override def listIndices(collection: CollectionName): Future[Seq[CompoundIndex]] = {
        client.listIndices(collection)
      }
    }

    case class Stub() extends Service {
      override def addIndexToCollection(collection: CollectionName, index: CompoundIndex): Future[VersionedResponse[Seq[CompoundIndex]]] = {
        println(s"addIndexToCollection($collection,$index)")
        Future.successful(InsertSuccess(0, Seq(index).versionedRecord()))
      }

      def replaceIndexInCollection(collection: CollectionName, oldIndex: CompoundIndex, newIndex: CompoundIndex): Future[VersionedResponse[Seq[CompoundIndex]]] = {
        println(s"replaceIndexInCollection($collection,$oldIndex, $newIndex)")
        Future.successful(InsertSuccess(0, Seq(newIndex).versionedRecord()))
      }

      override def removeIndexFromCollection(collection: CollectionName, index: CompoundIndex): Future[Option[VersionedResponse[Seq[CompoundIndex]]]] = {
        println(s"removeIndexFromCollection($collection,$index)")
        Future.successful(None)
      }

      override def listIndices(collection: CollectionName): Future[Seq[CompoundIndex]] = {
        println("listIndices()")
        Future.successful(Nil)
      }
    }

  }

}

/**
 * HTML control for listing, adding/removing compound indices for a particular collection
 *
 * @param collectionName
 */
case class CompoundIndexPage(collectionName: CollectionName, client: CompoundIndexPage.Service = CompoundIndexPage.Service(), refreshTheUIOnUpdate: Boolean = false) extends FutureImplicits {
  private val (alerts, errors) = {
    import scala.concurrent.duration._
    MessageAlerts.info(2.seconds) -> MessageAlerts.error(7.seconds)
  }

  private val container = div().render

  val indicesComponent: CompoundIndexComponent.ListIndices = CompoundIndexComponent.ListIndices(refreshTheUIOnUpdate)

  def onAddIndex(compoundIndex: CompoundIndex) = {
    client.addIndexToCollection(collectionName, compoundIndex).onComplete {
      case Failure(exception) =>
        errors.pushMessage(s"Error adding index: $exception")
      case Success(result) =>
        alerts.pushMessage(s"Added Index: $result")
        if (!refreshTheUIOnUpdate) {
          refreshIndices()
        }
    }
  }

  def onRemoveIndex(compoundIndex: CompoundIndex) = {
    client.removeIndexFromCollection(collectionName, compoundIndex).onComplete {
      case Failure(exception) =>
        errors.pushMessage(s"Error removing index: $exception")
      case Success(result) =>
        alerts.pushMessage(s"Removed Index: $result")
        if (!refreshTheUIOnUpdate) {
          refreshIndices()
        }
    }
  }

  def onUpdateIndex(before: CompoundIndex, after: CompoundIndex) = {
    client.replaceIndexInCollection(collectionName, before, after).onComplete {
      case Failure(exception) =>
        errors.pushMessage(s"Error updating index: $exception")
      case Success(result) =>
        alerts.pushMessage(s"Updated Index: $result")
        if (!refreshTheUIOnUpdate) {
          refreshIndices()
        }
    }
  }

  indicesComponent.onEvent {
    case ListItem.AddEvent(compoundIndex) => onAddIndex(compoundIndex)
    case ListItem.UpdateEvent(_, before, updated) => onUpdateIndex(before, updated)
    case ListItem.RemoveEvent(compoundIndex) => onRemoveIndex(compoundIndex)
  }

  def refreshIndices(): Unit = {
    container.innerHTML = ""
    client.listIndices(collectionName).onComplete {
      case Failure(exception) =>
        errors.pushMessage(s"Error listing indices: $exception")
      case Success(indices: Seq[CompoundIndex]) => indicesComponent.updateIndices(indices)
    }
  }

  def render() = {
    refreshIndices()
    div(
      indicesComponent.render(),
      alerts.messageDiv,
      errors.messageDiv
    ).render
  }
}
