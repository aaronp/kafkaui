package franz.client.pages.matches

import cats.data.{NonEmptyChain, Validated}
import cats.syntax.validated._
import franz.client.bootstrap.Component
import franz.client.js._
import franz.client.pages.Nav
import franz.data.index.{MatchWeights, RecordAssociations, ReferenceToValue}
import franz.data.{CollectionName, IndexValue, RecordCoords, ValuePath}
import io.circe.Json
import org.scalajs.dom.raw.Element
import scalatags.JsDom.all.{`class`, td, _}

case class AssociationsComponent(nav: Nav, associations: RecordAssociations, weights: MatchWeights = MatchWeights.empty) extends Component {

  override def render(): Element = {
    table(`class` := "table table-hover table-condensed table-striped table-sm")(
      thead(
        tr(
          th(style := "width: 20pt", `class` := "align-middle")("Collection"),
          th(style := "width: 20pt", `class` := "align-middle")("Id"),
          th(style := "width: 20pt", `class` := "align-middle")("Version"),
          th("Our Field"),
          th("Their Field"),
          th("Value")
        )
      ),
      tbody(
        rows
      )
    ).render
  }


  override def update(value: Json): Unit = {
  }

  override def validated(): Validated[NonEmptyChain[Component.Error], Json] = {
    Json.Null.validNec
  }

  private def rows = {
    val bc: RecordAssociations.ByCollection = associations.byCollection
    bc.collections.flatMap { collection =>
      val byId = bc.byCollection(collection)
      val totalIdsForThisCollection = byId.refsById.values.map(_.size).sum

      val mostMatchesFirst = byId.refsById.toList.sortBy(_._2.size)(Ordering.Int.reverse)
      mostMatchesFirst.zipWithIndex.flatMap {
        case ((_, refs: List[RecordAssociations.ByIdEntry]), collIdx) =>
          refs.zipWithIndex.map {
            case (entry, idIdx) =>
              val collectionCell = if (collIdx == 0) {
                Option(totalIdsForThisCollection)
              } else {
                None
              }
              makeRow(refs.size, entry, collectionCell, idIdx == 0)
          }
      }
    }
  }

  private def makeRow(numIds: Int, entry: RecordAssociations.ByIdEntry, collectionCol: Option[Int], isFirstId: Boolean) = {
    val RecordAssociations.ByIdEntry(ourPath: ValuePath, value: IndexValue, ReferenceToValue(collection, theirPath: ValuePath, id, version)) = entry
    val cells = List(
      td(`class` := "align-middle")(versionLink(collection, id, version)),
      td(`class` := "align-middle")(ourPath.mkString(".")),
      td(`class` := "align-middle")(theirPath.mkString(".")),
      td(value))

    val allCells = if (isFirstId) {
      val collectionCells = collectionCol.map { totalIdsForThisCollection =>
        td(rowspan := totalIdsForThisCollection, `class` := "align-middle")(collectionLink(collection))
      }.toList

      collectionCells ::: td(rowspan := numIds, `class` := "align-middle")(idLink(collection, id)) :: cells
    } else {
      cells
    }
    tr()(allCells)
  }

  private def idLink(collection: CollectionName, id: String) = {
    val link = a(href := "#")(id).render
    link.onclick = e => {
      e.cancel()
      nav.move.toRecord(RecordCoords.latest(collection, id))
    }
    link
  }

  private def versionLink(collection: CollectionName, id: String, version: Int) = {
    val link = a(href := "#")(version).render
    link.onclick = e => {
      e.cancel()
      nav.move.toRecord(RecordCoords(collection, id, version))
    }
    link
  }

  private def collectionLink(collection: CollectionName) = {
    val link = a(href := "#")(collection).render
    link.onclick = e => {
      e.cancel()
      nav.move.toCollection(collection)
    }
    link
  }

}
