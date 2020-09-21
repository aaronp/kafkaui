package franz.client.pages

import franz.client.js.appClientFuture
import franz.data.{CollectionName, Id, RecordCoords, VersionedJson}
import org.scalajs.dom.html.Div
import scalatags.JsDom.all._

import scala.concurrent.ExecutionContext.Implicits._

/**
 * A page where we have a collection and id but no version, so we load all the versions
 *
 * and allow the user to create a new one
 *
 * @param nav
 * @param collection
 * @param id
 */
case class CollectionIdPage(nav: Nav, collection: CollectionName, id: Id) {

  val readService = appClientFuture.crudClient.readService

  private var recordsByVersion = Map[Int, VersionedJson]()
  private var cellsByVersion = Map[Int, Div]()

  val resultsDiv = div().render

  /**
   * We're reading from the bottom (first version) up ... continue until we meet in the middle
   *
   * @param record
   */
  private def loadRecursive(record: VersionedJson, ascending: Boolean): Unit = {
    val recordFound = recordsByVersion.contains(record.version)
    if (!recordFound) {
      recordsByVersion = recordsByVersion.updated(record.version, record)
      refreshVersions()
      val nextCoords = if (ascending) {
        RecordCoords.next(collection, id, record.version)
      } else {
        RecordCoords.previous(collection, id, record.version)
      }
      readService.read(nextCoords).foreach {
        case None =>
        case Some(latest) => loadRecursive(latest, ascending)
      }
    }
  }

  def renderVersion(version: Int, record: VersionedJson): Div = {
    SearchResultsSnippet.renderItem(version, nav, collection, record).render
  }

  private def refreshVersions() = {
    val missing: Map[Int, VersionedJson] = recordsByVersion -- cellsByVersion.keySet

    missing.foreach {
      case (version, record) =>
        val newDiv = renderVersion(version, record)
        val beforeRef: Option[(Int, Div)] = cellsByVersion.toList.sortBy(_._1).dropWhile(_._1 <= version).headOption
        beforeRef match {
          case Some((r, b4)) => resultsDiv.insertBefore(newDiv, b4)
          case None => resultsDiv.appendChild(newDiv)
        }
        cellsByVersion = cellsByVersion.updated(version, newDiv)
    }
  }

  /**
   * Kick off some requests from both ends and stop when our 'versions' map is filled
   */
  private def populate() = {
    readService.read(RecordCoords.latest(collection, id)).foreach {
      case None =>
      case Some(latest) => loadRecursive(latest, false)
    }
    readService.read(RecordCoords(collection, id, 0)).foreach {
      case None =>
      case Some(versionZero) => loadRecursive(versionZero, true)
    }
  }

  def render(): Div = {
    populate()

    div(
      resultsDiv
    ).render
  }
}
