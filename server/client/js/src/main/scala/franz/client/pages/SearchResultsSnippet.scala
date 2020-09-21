package franz.client.pages

import franz.client.js._
import franz.data.crud.Search
import franz.data.{CollectionName, VersionedJson}
import org.scalajs.dom.html.Div
import scalatags.JsDom.all._

object SearchResultsSnippet {

  def renderItem(offset: Int, nav: Nav, collectionName: CollectionName, record: VersionedJson) = {
    val open = a(href := "#", `class` := "btn btn-link")(s"#${offset}").render
    open.onclick = e => {
      e.cancel()

      nav.move.toRecord(collectionName, record)
    }

    val created = java.time.Instant.ofEpochMilli(record.createdEpochMillis)
    div(`class` := "row")(
      div(`class` := "col-lg-1")(open),
      div(`class` := "col-lg-2")(record.id),
      div(`class` := "col-lg-1")(s"version ${record.version}"),
      div(`class` := "col-lg-1")(s"Created $created"),
      div(`class` := "col-lg-1")(record.userId),
      div(`class` := "col-lg-6")(record.data.noSpaces)
    )
  }
}

case class SearchResultsSnippet(nav: Nav, collectionName: CollectionName, response: Search.Response) {

  def render(): Div = {
    val rows = response.records.zipWithIndex.map {
      case (value, i) => SearchResultsSnippet.renderItem(response.offset + i + 1, nav, collectionName, value)
    }

    val from = response.total match {
      case 0 => 0
      case _ => response.offset + 1
    }
    val header = div(`class` := "row")(
      div(`class` := "col-lg")(
        h4(`class` := "text-left")(s"${from} to ${response.toOffset} of ${response.total} Results")
      )
    )

    div((header +: rows): _*).render
  }
}
