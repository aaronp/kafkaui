package franz.client.pages

import franz.client.js.{FutureImplicits, appClientFuture, _}
import franz.client.pages.compoundindex.CompoundIndexPage
import franz.data.crud.Search
import franz.data.{CollectionName, QueryRange}
import org.scalajs.dom.html.Div
import scalatags.JsDom.all._

import scala.concurrent.ExecutionContext.Implicits._
import scala.util.{Failure, Success}

class CollectionPage(nav: Nav, collectionName: CollectionName) extends scalatags.LowPriorityImplicits with FutureImplicits {

  def focus() = searchInput.focus()

  val collection = CollectionSelect {
    case (_, selected) =>
      nav.move.toCollection(selected)
  }

  // we can create new values in the collection here
  val createForm = new CreateDataForm(collectionName, nav)
  collection.refresh()


  val searchButton = button(`class` := "btn btn-link",
    `type` := "button",
    attr("data-toggle") := "collapse",
    attr("data-target") := "#collapseOne",
    attr("aria-expanded") := "true",
    attr("aria-controls") := "collapseOne")("Search").render

  searchButton.onclick = e => {
    e.cancel()
    doSearch(searchInput.value)
  }

  val searchInput = input(`class` := "form-control", id := "searchInput", placeholder := "e.g. <id> or <x.y.z = 12>").render
  searchInput.onkeyup = e => {
    e.onEnter {
      e.cancel()
      doSearch(searchInput.value)
    }
  }

  val resultsDiv = div(id := "results").render

  val toggleSearchButton = button(`class` := "btn btn-link collapsed",
    `type` := "button",
    attr("data-toggle") := "collapse",
    attr("data-target") := "#collapseOne",
    attr("aria-expanded") := "false",
    attr("aria-controls") := "collapseOne")("Search").render

  val toggleCreateButton = button(`class` := "btn btn-link collapsed",
    `type` := "button",
    attr("data-toggle") := "collapse",
    attr("data-target") := "#collapseTwo",
    attr("aria-expanded") := "false",
    attr("aria-controls") := "collapseTwo")("Create").render

  val toggleIndicesButton = button(`class` := "btn btn-link collapsed",
    `type` := "button",
    attr("data-toggle") := "collapse",
    attr("data-target") := "#collapseThree",
    attr("aria-expanded") := "false",
    attr("aria-controls") := "collapseThree")("Indices").render


  def render(): Div = {
    div()(
      h4(s"${collectionName}"),
      accordianDiv
    ).render
  }

  def accordianDiv() = {
    div(`class` := "accordion", `id` := "accordionContainer")(
      div(`class` := "card")(
        div(`class` := "card-header", `id` := "headingOne")(
          h5(`class` := "mb-0")(toggleSearchButton)
        ),
        div(`id` := "collapseOne", `class` := "collapse show", attr("aria-labelledby") := "headingOne", attr("data-parent") := "#accordionContainer")(
          div(`class` := "card-body")(
            div(`class` := "input-group")(
              div(`class` := "input-group-prepend")(
                span(`class` := "input-group-text", `id` := searchInput.id)("Query:")
              ),
              searchInput,
              raw("&nbsp;"),
              searchButton
            ),
            resultsDiv
          )
        )
      ),
      div(`class` := "card")(
        div(`class` := "card-header", `id` := "headingTwo")(
          h5(`class` := "mb-0")(
            toggleCreateButton
          )
        ),
        div(`id` := "collapseTwo", `class` := "collapse", attr("aria-labelledby") := "headingTwo", attr("data-parent") := "#accordionContainer")(
          div(`class` := "card-body")(
            createForm.render
          )
        )
      ),
      div(`class` := "card")(
        div(`class` := "card-header", `id` := "headingThree")(
          h5(`class` := "mb-0")(
            toggleIndicesButton
          )
        ),
        div(`id` := "collapseThree", `class` := "collapse", attr("aria-labelledby") := "headingThree", attr("data-parent") := "#accordionContainer")(
          div(`class` := "card-body")(
            CompoundIndexPage(collectionName).render()
          )
        )
      )
    ).render
  }

  // TODO - add controls for this
  def limit = QueryRange(0, 100)

  def appendSearchResults(response: Search.Response) = {
    resultsDiv.innerHTML = ""
    val results = SearchResultsSnippet(nav, collectionName, response).render()
    resultsDiv.appendChild(results)
  }

  def doSearch(queryString: String) = {
    appClientFuture.searchClient.search(Search.Request(collectionName, queryString, limit)).onComplete {
      case Success(input) => appendSearchResults(input)
      case Failure(err) => createForm.errors.pushMessage(s"error: ${err.getMessage}")
    }
  }
}
