package franz.client.bootstrap

import cats.data.{NonEmptyChain, Validated}
import cats.syntax.validated._
import franz.client.bootstrap.NavStack._
import franz.client.js.{HtmlUtils, _}
import io.circe.Json
import io.circe.syntax._
import org.scalajs.dom.Element
import scalatags.JsDom.all.{`class`, li, _}
import scalatags.JsDom.tags2.nav

/**
 * A NavStack keeps a breadcrumb hierarchy using [[StackElement]]s to represent each step in a path
 */
object NavStack {

  /**
   * A caching element
   *
   * @param nav
   * @param name
   * @param content
   */
  class StackElement(nav: NavStack, val name: String, content: => Component) {

    val id = uniqueId()

    override def toString: String = s"StackElement($name, $id)"

    override def equals(other: Any): Boolean = other match {
      case e: StackElement => id == e.id
      case _ => false
    }

    override def hashCode() = id.hashCode

    val navLink = a(href := "#")(name).render
    navLink.onclick = e => {
      e.cancel()
      nav.show(this)
    }

    def updateLinkText(newLinkText: String): Any = {
      navLink.innerHTML = newLinkText
    }

    private var cachedComponent = Option.empty[Component]
    private var cachedElement = Option.empty[Element]

    def render(): Element = {
      cachedElement.getOrElse {
        val e = component().render()
        cachedElement = Option(e)
        e
      }
    }

    def component(): Component = {
      cachedComponent.getOrElse {
        val c = content
        cachedComponent = Option(c)
        c
      }
    }

    def stateOpt(): Option[Json] = validatedOpt.flatMap(_.toOption)

    def validatedOpt(): Option[Validated[NonEmptyChain[Component.Error], Json]] = {
      cachedComponent.map(_.validated())
    }

    def state(): Json = Json.obj(
      "name" -> name.asJson,
      "state" -> stateOpt().getOrElse(Json.Null))
  }

}

case class NavStack(targetDivId: String,
                    navDivClass: String = "col-lg",
                    componentRegistryByKid: Map[String, Component] = Map.empty) extends Component with HtmlUtils {


  def remove(elm: StackElement, onlyThisElement: Boolean = false) = {
    if (onlyThisElement) {
      stack = stack diff (List(elm))
    } else {
      val after = stack.dropWhile(_.id != elm.id)
      if (after.headOption.exists(_.id == elm.id)) {
        stack = after.tail
        refresh()
      }
    }
  }

  private var stack = List[StackElement]()

  override def validated(): Validated[NonEmptyChain[Component.Error], Json] = {
    val array = stack.map { elm =>
      elm.state()
    }
    Json.arr(array: _*).validNec
  }

  override def update(value: Json): Unit = {
    // To do this correctly we'd need to know how/what components to make
    value.asArray.foreach { states =>

    }
  }

  private def refresh() = {
    navOL.innerHTML = ""
    stack.reverse.foreach { elem =>
      navOL.appendChild(item(elem.navLink))
    }
  }

  private val navOL = ol(`class` := "breadcrumb", `id` := uniqueId())().render

  def push(elem: StackElement): StackElement = {
    stack = elem +: stack
    navOL.appendChild(item(elem.navLink))
    elem
  }

  def push(name: String, content: Component): StackElement = push(new StackElement(this, name, content))

  def push(name: String)(content: => Component): StackElement = {
    push(new StackElement(this, name, content))
  }

  private def item(inner: Element) = {
    li(`class` := "breadcrumb-item")(inner).render
  }

  private lazy val targetDiv = $(targetDivId)

  def show(name: String): Unit = {
    stack.find(_.name == name).foreach(show)
  }

  def show(elem: StackElement): Unit = {
    targetDiv.innerHTML = ""
    targetDiv.appendChild(elem.render())
  }

  override def render(): Element = {
    nav(attr("aria-label") := "breadcrumb")(
      navOL
    ).render
  }

  def renderDiv(): Element = {
    div(`class` := "row")(
      div(`class` := navDivClass)(
        render()
      )
    ).render
  }
}
