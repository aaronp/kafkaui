package franz.client.bootstrap

import cats.data.{NonEmptyChain, Validated}
import cats.syntax.validated._
import franz.client.js._
import io.circe.Json
import scalatags.JsDom.all.{`class`, div, id, _}

case class DropDownChoiceInput(labelValue: String,
                               initialChoices: List[String] = Nil,
                               buttonClass: String = "btn btn-secondary dropdown-toggle",
                               labelClass: String = "control-label col-lg-1",
                               onSelectCallback: String => Unit = _ => ()) extends Component {
  val selectInput = button(`class` := buttonClass,
    `type` := "button",
    `id` := uniqueId(),
    attr("data-toggle") := "dropdown",
    attr("aria-haspopup") := "true",
    attr("aria-expanded") := "false")(
    labelValue
  ).render

  private val selectionList = div(`class` := "dropdown-menu", attr("aria-labelledby") := selectInput.id).render

  updateOptionText(initialChoices)

  def onChange(f: DropDownChoiceInput => Unit) = {
    selectInput.onchange = e => {
      e.cancel()
      f(this)
    }

    selectInput.onkeyup = onEnterCancel {
      f(this)
    }
  }

  private var selectedOption = Option.empty[String]

  def onSelect(name: String) = {
    selectedOption = Option(name)
  }

  def updateOptionText(newValues: List[String]): Unit = {
    selectionList.innerHTML = ""
    newValues.map(makeChoice).foreach(selectionList.appendChild)
  }

  def makeChoice(name: String) = {
    val choice = a(`class` := "dropdown-item", `href` := "#")(name).render
    choice
  }

  override def render() = {
    div(`class` := "dropdown")(
      selectInput,
      selectionList
    ).render
  }

  override def validated(): Validated[NonEmptyChain[Component.Error], Json] = {
    selectedOption match {
      case None => Component.Error(s"$labelValue has not been selected").invalidNec
      case Some(selection) => Json.fromString(selection).validNec
    }
  }

  def currentSelection(): String = selectInput.value.trim

  override def update(value: Json): Unit = selectInput.value = JsonAsString(value)
}
