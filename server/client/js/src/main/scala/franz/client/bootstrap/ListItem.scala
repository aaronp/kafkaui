package franz.client.bootstrap


import java.util.UUID

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyChain, Validated}
import cats.syntax.validated._
import franz.client.bootstrap.ListItem.{AddEvent, ListItemEvent, RemoveEvent, UpdateEvent}
import franz.client.js.{HtmlUtils, MessageAlerts, _}
import io.circe.Json
import monix.execution.Scheduler.Implicits.global
import monix.reactive.subjects.ConcurrentSubject
import org.scalajs.dom.html.{Div, TableRow}
import org.scalajs.dom.raw.Element
import scalatags.JsDom.all.{`class`, a, div, h5, h6, p, span, _}

import scala.collection.mutable.ListBuffer


object ListItem {
  type ValueForContent = Json => String
  type Label = String

  /**
   * Each table cell has a header label and some way to render the content from the record's json value
   */
  type Cell = (Label, ValueForContent)


  sealed trait ListItemEvent[A]

  case class AddEvent[A](added: A) extends ListItemEvent[A]

  case class UpdateEvent[A](index: Int, before: A, after: A) extends ListItemEvent[A]

  case class RemoveEvent[A](removed: A) extends ListItemEvent[A]

}

/**
 *
 * List Items - add components to a table. producing a json array
 *
 * Loads of bloated code to just add/remove stuff from a table
 *
 * @param inner             the inner component which will be used to stamp-out values
 * @param tableCellsByLabel a list of column names/functions to display the contents of that column
 * @param title             the title of the card
 * @param subTitle
 * @param instructionText
 * @param addLabel
 * @param updateLabel
 * @param removeLabel
 * @param addButtonClass
 * @param autoAdd           if true the table will add/remove elements when the user interacts w/ the controls. If false, then the updating
 *                          of the table is left to something else. Typically something will create a ListItem and subscribe to the table events
 *                          using 'onEvent { ... }', make some kind of REST call, and then update the table when the REST response returns.
 */
case class ListItem(inner: Component,
                    tableCellsByLabel: List[ListItem.Cell],
                    title: String,
                    subTitle: String = "",
                    instructionText: String = "",
                    addLabel: String = "add",
                    updateLabel: String = "update",
                    removeLabel: String = "remove",
                    addButtonClass: String = "btn btn-primary",
                    tableClass : String = "table table-bordered table-striped table-sm",
                    autoAdd: Boolean = true) extends Component {

  val events = ConcurrentSubject.publish[ListItemEvent[Json]]

  if (autoAdd) {
    events.dump("table event").foreach {
      case AddEvent(json) => addItem(json)
      case UpdateEvent(index, _, json) => replaceElement(index, json)
      case RemoveEvent(json) => remove(json)
    }
  }

  /**
   * A wrapper around each list item
   */
  private final class Wrapper(val jsonValue: Json, val id: String = UUID.randomUUID().toString) {
    wrapper =>
    override def toString: String = s"wrapper($label, $jsonValue, $id)"

    def rowId = s"${id}-row"

    def cells: List[String] = tableCellsByLabel.map(_._2(jsonValue))

    override def equals(obj: Any): Boolean = obj match {
      case w: Wrapper => id == w.id
      case _ => false
    }

    override def hashCode(): Int = id.hashCode()
  }

  private val buffer = ListBuffer[Wrapper]()

  private val tableBody = tbody().render

  private val tableHeader = thead().render

  private def tableHeaderRow: TableRow = {
    // the remove column followed by the headers
    def headerElm(name: String): Element = th(name).render.asInstanceOf[Element]

    val removeHeader = th(`style` := "width:20pt")().render.asInstanceOf[Element]

    val cols = removeHeader +: tableCellsByLabel.map {
      case (label, _) => headerElm(label)
    }
    tr(cols).render
  }

  private val tableContainer = {
    table(`class` := tableClass)(
      tableHeader,
      tableBody
    ).render
  }

  /**
   * Trigger the adding of the inner component to this list
   */
  def onAddItem() = {
    inner.validated() match {
      case Valid(json) =>
        events.onNext(ListItem.AddEvent(json))
      case Invalid(errors) =>
        errors.iterator.foreach { msg =>
          errorAlerts.pushMessage(msg.message)
        }
    }
  }

  def clear() = {
    buffer.clear() // remove the elements
    deselectRow() // remove any selection
    clearTableHTML() // clear the table contents
    inner.update(Json.Null) // update/reset the inner control
  }

  val addButton = a(`href` := "#", `class` := addButtonClass)(addLabel).render
  addButton.onclick = e => {
    e.cancel()
    onAddItem()
  }

  val updateButton = a(`href` := "#", `class` := "px-2 card-link", style := "display:none")(updateLabel).render
  updateButton.onclick = e => {
    e.cancel()

    currentRow.foreach { elm =>
      replaceElementWrapper(elm)
    }
  }


  private def removeCurrentRowClass() = {
    currentRow.foreach { oldRow =>
      val trElm = HtmlUtils.$(oldRow.rowId).asInstanceOf[TableRow]
      trElm.className = ""
    }
  }

  private def setCurrent(elm: Wrapper) = {
    removeCurrentRowClass()
    currentRow = Option(elm)
    val c = HtmlUtils.$(elm.rowId).asInstanceOf[TableRow]
    c.className = "table-primary"
  }

  private def replaceElementWrapper(oldElement: Wrapper): Unit = {
    val i = buffer.indexOf(oldElement)
    if (i >= 0) {
      inner.validated() match {
        case Valid(json) =>
          events.onNext(UpdateEvent(i, oldElement.jsonValue, json))
        case Invalid(errors) =>
          errors.iterator.foreach { msg =>
            errorAlerts.pushMessage(msg.message)
          }
      }
    }
  }

  def replaceElement(index: Int, newJson: Json) = {
    buffer.lift(index).fold(false) { oldElement =>
      val newElm = new Wrapper(newJson, id = oldElement.id)
      buffer.update(index, newElm)
      val newTR = makeTableRow(newElm)
      val oldTR = HtmlUtils.$(oldElement.rowId)
      tableBody.replaceChild(newTR, oldTR)
      setCurrent(newElm)
      true
    }
  }

  def toggleUpdateVisible() = {
    val visibleStyle = currentRow.fold("display:none")(_ => "display:inline")
    updateButton.style = visibleStyle
  }

  val errorAlerts = MessageAlerts.error()

  def remove(json: Json): Unit = {
    buffer.find(_.jsonValue == json).foreach(removeItem)
  }

  def remove(index: Int): Unit = {
    buffer.lift(index).foreach(removeItem)
  }

  private def removeItem(element: Wrapper): Boolean = {
    currentRow.filter(_.id == element.id).foreach { _ =>
      deselectRow()
    }
    val removed = buffer.contains(element)


    if (removed) {
      buffer.subtractOne(element)
      events.onNext(RemoveEvent(element.jsonValue))
      // redraw the table
      clearTableHTML()
      buffer.foreach(addItem)
      removed
    } else false
  }

  private var currentRow = Option.empty[Wrapper]

  private def deselectRow(): Unit = {
    removeCurrentRowClass()
    currentRow = None
    toggleUpdateVisible()
  }

  private def toggleRow(element: Wrapper): Unit = {
    if (currentRow.exists(_.id == element.id)) {
      deselectRow()
    } else {
      selectRow(element)
    }
  }

  private def selectRow(element: Wrapper): Unit = {
    inner.update(element.jsonValue)
    setCurrent(element)
    toggleUpdateVisible()
  }

  def addItem(json: Json): Unit = {
    val wrapper = new Wrapper(json)
    if (buffer.isEmpty) {
      tableHeader.appendChild(tableHeaderRow)
    }
    buffer.addOne(wrapper)
    addItem(wrapper)
  }

  private def clearTableHTML() = {
    if (buffer.isEmpty) {
      tableHeader.innerHTML = ""
    }
    tableBody.innerHTML = ""
  }

  private def makeTableRow(element: Wrapper): TableRow = {
    val removeRowBtn = a(`href` := "#", `class` := "card-link")(img(src := "/img/x-circle.svg")(removeLabel)).render
    removeRowBtn.onclick = e => {
      e.cancel()
      removeItem(element)
    }

    val removeRowTD = td(removeRowBtn).render
    removeRowTD.onclick = e => {
      e.cancel()
      toggleRow(element)
    }

    def mkCell(content: String) = {
      val setRowBtn = a(`href` := "#", `class` := "card-link")(content).render

      val elm = td(setRowBtn).render
      elm.onclick = e => {
        e.cancel()
        toggleRow(element)
      }
      elm
    }

    val tds = element.cells.map(mkCell)
    tr(id := element.rowId)(removeRowTD +: tds).render
  }

  private def addItem(element: Wrapper) = {
    tableBody.appendChild(makeTableRow(element))
  }

  override def update(value: Json): Unit = {
    clear()
    value.asArray match {
      case None =>
      case Some(arr) => arr.foreach(addItem)
    }
  }

  override def render(): Div = {
    div(`class` := "card")(
      div(`class` := "card-body")(
        h5(`class` := "card-title")(title),
        if (subTitle != "") h6(`class` := "card-subtitle mb-2 text-muted")(subTitle) else span(),
        if (instructionText != "") p(`class` := "card-text")(instructionText) else span(),
        inner.render(),
        div(`class` := "row py-4")(addButton, updateButton),
        tableContainer,
        errorAlerts.messageDiv
      )
    ).render
  }

  def jsonValue(): Json = {
    val values = buffer.map(_.jsonValue).toList
    Json.arr(values: _*)
  }

  override def validated(): Validated[NonEmptyChain[Component.Error], Json] = {
    jsonValue().validNec
  }
}
