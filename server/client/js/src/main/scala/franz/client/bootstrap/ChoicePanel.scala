package franz.client.bootstrap

import cats.data.{NonEmptyChain, Validated}
import franz.client.bootstrap.ChoicePanel.SelectChoiceComponent
import franz.client.js._
import io.circe.Json
import io.circe.syntax._
import org.scalajs.dom.html.Anchor
import org.scalajs.dom.raw.Element
import scalatags.JsDom.all.{`class`, a, attr, div, href, id, role, _}

/**
 * codified the list-group: https://getbootstrap.com/docs/4.0/components/list-group/
 *
 *
 * A ChoicePanel is the 'div' content of a possible set of choices, contained by a [[SelectChoiceComponent]]
 *
 * @param index
 * @param labelValue
 * @param initialActive
 * @param control
 */
case class ChoicePanel(idPrefix: String,
                       index: Int,
                       labelValue: String,
                       initialActive: Boolean,
                       control: Component,
                       style: ChoicePanel.Style) extends Component.Base(control) {
  val divId = s"ctl-div-$idPrefix"
  val linkId = s"ctl-link-$idPrefix"
  var active = initialActive

  private def setActive(selected: Boolean) = {
    active = selected
    hyperlink.className = style.linkClass(this)
    HtmlUtils.divById(divId).foreach { container =>
      container.className = divClass()
    }
  }

  lazy val hyperlink = style.linkFor(this)

  def renderParent(): Element = super.render

  def divClass() = if (active) "tab-pane fade show active" else "tab-pane fade"

  override def render(): Element = {
    div(`class` := divClass(), `id` := divId, `role` := "tabpanel", attr("aria-labelledby") := linkId)(renderParent).render
  }

}

object ChoicePanel {

  trait Style {
    def linkFor(panel: ChoicePanel): Anchor

    def linkClass(panel: ChoicePanel): String
  }

  object Style {

    case object Vertical extends Style {
      override def linkClass(panel: ChoicePanel): String = if (panel.active) "list-group-item list-group-item-action active" else "list-group-item list-group-item-action"

      override def linkFor(panel: ChoicePanel): Anchor = {
        import panel._
        a(`class` := linkClass(panel),
          id := linkId,
          attr("data-toggle") := "list",
          href := s"#$divId",
          role := "tab",
          attr("aria-controls") := labelValue)(labelValue).render
      }
    }


    case object Tab extends Style {

      def linkClass(panel: ChoicePanel) = {
        if (panel.active) "nav-link active" else "nav-link"
      }

      override def linkFor(panel: ChoicePanel) = {
        import panel._
        a(`class` := linkClass(panel),
          id := linkId,
          attr("data-toggle") := "tab",
          href := s"#$divId",
          role := "tab",
          attr("aria-controls") := labelValue,
          attr("aria-selected") := active)(labelValue).render
      }
    }

  }

  val PanelDataKey = "panelData"

  def apply(first: (String, Component), theRest: (String, Component)*): SelectChoiceComponent = {
    vertical(first +: theRest.toList)
  }

  def vertical(first: (String, Component), theRest: (String, Component)*): SelectChoiceComponent = {
    vertical(first +: theRest.toList)
  }

  def vertical(children: List[(String, Component)]): SelectChoiceComponent = apply(children, Style.Vertical)

  def horizontal(first: (String, Component), theRest: (String, Component)*): SelectChoiceComponent = {
    horizontal(first +: theRest.toList)
  }

  def horizontal(children: List[(String, Component)]): SelectChoiceComponent = apply(children, Style.Tab)

  def apply(children: List[(String, Component)], style: Style): SelectChoiceComponent = {
    val prefix = uniqueId()
    val panels = children.zipWithIndex.map {
      case ((name, c), i) =>
        ChoicePanel(s"$prefix-$i", i, name, i == 0, c, style)
    }
    new SelectChoiceComponent(panels, style)
  }

  /**
   * Let's the user choose which kind of StemOp they wanna make
   */
  class SelectChoiceComponent(controls: List[ChoicePanel], style: ChoicePanel.Style) extends Component {
    private var currentStemOpComponent: ChoicePanel = controls.head

    def currentIndex(): Int = currentStemOpComponent.index

    def currentLabel(): String = currentStemOpComponent.labelValue

    def currentComponent(): Component = currentStemOpComponent.control

    override def validated(): Validated[NonEmptyChain[Component.Error], Json] = {
      val c = currentStemOpComponent
      c.validated().map { panelJson =>
        withIndex(panelJson, c.index)
      }
    }

    private def setActive(ctrl: ChoicePanel) = {
      currentStemOpComponent = ctrl
      controls.foreach { panel: ChoicePanel =>
        panel.setActive(panel.index == ctrl.index)
      }
    }

    /**
     * If this panel is adding elements to a list, then when we 'update' this panel
     * we want to make active whatever element is represented by that json
     *
     * @param value
     */
    override def update(value: Json): Unit = {
      val indexOpt = indexForJson(value)
      // the currentStemOpComponent may not be the same one as we're updating
      val ctrl: ChoicePanel = indexOpt.fold(currentStemOpComponent) { idx =>
        val found = controls.apply(idx)
        found
      }

      setActive(ctrl)

      // we may want to update w/ 'Null'
      dataForJson(value) match {
        case Some(d8a) =>
          currentStemOpComponent.update(d8a)
        case None =>
          currentStemOpComponent.update(value)
      }
    }

    override def render = {
      style match {
        case Style.Vertical => renderVertically
        case Style.Tab => renderAsTabs
      }
    }

    def renderAsTabs = {

      val links = controls.map { panel =>
        panel.hyperlink.onclick = _ => {
          currentStemOpComponent = panel
        }
        li(`class` := "nav-item")(panel.hyperlink)
      }

      val contents = controls.map(_.render())
      div()(
        ul(`class` := "nav nav-tabs", `id` := uniqueId(), `role` := "tablist")(links),
        div(`class` := "tab-content", `id` := uniqueId())(contents)
      ).render
    }

    def renderVertically = {
      val links = controls.map { panel =>
        panel.hyperlink.onclick = _ => {
          currentStemOpComponent = panel
        }
        panel.hyperlink
      }
      val contents = controls.map(_.render())

      div(`class` := "row")(
        div(`class` := "col-4")(
          div(`class` := "list-group", `id` := "list-tab", `role` := "tablist")(links)
        ),
        div(`class` := "col-8")(
          div(`class` := "tab-content", `id` := "nav-tabContent")(contents)
        )
      ).render
    }

  }


  def withIndex(value: Json, i: Int): Json = {
    Json.obj(
      "index" -> i.asJson,
      PanelDataKey -> value
    )
  }

  def dataForJsonTry(value: Json): Json = dataForJson(value).getOrElse(value)

  def dataForJson(value: Json): Option[Json] = {
    for {
      obj <- value.asObject
      panelData <- obj(PanelDataKey)
    } yield panelData
  }

  def indexForJson(value: Json) = {
    for {
      obj <- value.asObject
      index <- obj("index")
      i <- index.as[Int].toOption
    } yield i
  }
}
