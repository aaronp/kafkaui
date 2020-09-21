package franz.client.pages

import franz.client.js._
import franz.client.pages.NavLocation._
import franz.client.pages.matches.MatchesPage
import franz.client.state.AppState
import franz.data.{CollectionName, Id, RecordCoords, VersionedJson}
import org.scalajs.dom.Element
import org.scalajs.dom.html.{Anchor, Div}
import org.scalajs.dom.raw.HTMLElement
import scalatags.JsDom.all._

import scala.concurrent.ExecutionContext.Implicits._
import scala.scalajs.js.annotation.JSExportTopLevel
import scala.util.{Failure, Success}


@JSExportTopLevel("Nav")
case class Nav(navContainer: Element, navContentDiv: Div) {
  self =>

  /**
   * This is the public API of the nav so that various pages can just jump around, and Nav can update the ribbon (breadcrumbs),
   * app state, history, etc and then set the content
   *
   */
  object move {

    def toDiff(coords: RecordCoords): Unit = {
      navBarUpdateAndRender(NavLocation(coords, "diff"))
      val page = new DiffPage(self, coords)
      setPage(page.render)
    }

    def toMatches(coords: RecordCoords): Unit = {
      navBarUpdateAndRender(NavLocation(coords, "matches"))
      val page: MatchesPage = new MatchesPage(self, coords)
      setPage(page.render)
    }

    def toRecord(coords: RecordCoords): Unit = {
      appClientFuture.crudClient.readService.read(coords).onComplete {
        case Success(None) =>
          navBarUpdateAndRender(NavLocation(coords))
          val page = new DiffPage(self, coords)
          setPage(page.render)
        case Success(Some(found)) =>
          toRecord(coords.collection, found)
        case Failure(err) => HtmlUtils.raiseError(s"Loading $coords failed with $err")
      }

      navBarUpdateAndRender(NavLocation(coords))
      showRecordPage(new RecordPage(self, coords))
    }

    def toRecord(collectionName: CollectionName, data: VersionedJson): Unit = {
      val coords = RecordCoords(collectionName, data.id, data.version)
      navBarUpdateAndRender(NavLocation(coords))

      val page = new RecordPage(self, coords)
      page.jsonArea.value = data.data.spaces2
      showRecordPage(page)
    }

    def toCollection(collection: CollectionName): Unit = {
      navBarUpdateAndRender(NavLocation.forCollection(collection))
      showCollectionPage(collection)
    }

    def home() = {
      navBarUpdateAndRender(NavLocation.Home)
      showHomePage()
    }
  }


  private def navBarUpdateAndRender(where: NavLocation) = {
    AppState.get().updateIfLonger(where)
    navBarRender(where)
  }

  private val homeLink = {
    val homeA = a(href := "#")("Home").render
    homeA.onclick = e => {
      e.cancel()
      move.home()
    }
    homeA
  }

  private def collectionLink(collectionName: CollectionName) = {
    val collectionA = a(href := "#")(collectionName).render
    collectionA.onclick = e => {
      e.cancel()
      showCollectionPage(collectionName)
    }
    collectionA
  }

  private def idLink(collectionName: CollectionName, id: Id): Anchor = {
    val collectionA = a(href := "#")(id).render
    collectionA.onclick = e => {
      e.cancel()

      // leave the path as-is, just show the page
      showCollectionIdPage(collectionName, id)
    }
    collectionA
  }

  private def recordLink(coords: RecordCoords) = {
    val collectionA = a(href := "#")(coords.version.queryValue).render
    collectionA.onclick = e => {
      e.cancel()
      move.toRecord(coords)
    }
    collectionA
  }

  /**
   * A page which will expose operations for a collection to the user:
   *
   * $ query/search
   * $ permissions
   * $ stats (num records, etc)
   *
   * @param collectionName
   */
  private def showCollectionPage(collectionName: CollectionName) = {
    val page = new CollectionPage(this, collectionName)
    setPage(page.render)
    page.focus()
  }

  /**
   * A page which will expose operations for a collection and ID to the user:
   *
   * $ quick link for latest
   * $ version history / diff two
   *
   * @param collectionName
   * @param id
   */
  private def showCollectionIdPage(collectionName: CollectionName, id: Id) = {
    val page = CollectionIdPage(self, collectionName, id)
    setPage(page.render())
  }

  /**
   * Place where the user can choose a collection
   */
  private def showHomePage() = {
    setPage(new HomePage(this).render)
  }

  /**
   * A page which will expose operations for a collection record:
   *
   * $ The record UI or raw json
   * $ link to all the stuff you can do w/ a record:
   * schema, diff, update, undo, schemas, matches, prev/next versions
   *
   * @param page
   */
  private def showRecordPage(page: RecordPage) = {
    setPage(page.render)
  }

  private def renderRecordLocation(record: RecordLocation) = {
    // we always have a collection
    navContainer.appendChild(item(collectionLink(record.collection)))

    // and maybe an ID
    record.idOpt.foreach { id =>
      navContainer.appendChild(item(idLink(record.collection, id)))
    }

    // and maybe coords
    record.coordsOpt.foreach { coords =>
      navContainer.appendChild(item(recordLink(coords)))
    }

    record.asPath.drop(3).foreach { extra =>
      navContainer.appendChild(item(span(extra).render))
    }
  }

  /**
   * Just update the navigation currentPath.
   *
   * The nav knows what it's container is, so this is a side-effect
   *
   * @param location the location to render
   */
  private def navBarRender(location: NavLocation): Unit = {
    navContainer.innerHTML = ""
    location match {
      case NavLocation.Home => navContainer.appendChild(item(homeLink))
      case record: RecordLocation =>
        navContainer.appendChild(item(homeLink))
        renderRecordLocation(record)
    }
  }

  private def item(inner: Element) = li(`class` := "breadcrumb-item")(inner).render

  private def setPage(content: HTMLElement) = {
    navContentDiv.innerHTML = ""
    navContentDiv.appendChild(content)
  }
}

object Nav extends HtmlUtils {

  def apply(navContainerId: String, mainContainerId: String): Nav = {
    val main = divById(mainContainerId).getOrElse(sys.error(s"Can't find the mai div: $mainContainerId"))
    val nav = new Nav($(navContainerId), main)
    nav.navBarUpdateAndRender(AppState.get().currentPath)
    nav
  }
}
