package franz.client.bootstrap

import franz.client.js._
import org.scalajs.dom.html.Div
import org.scalajs.dom.raw.Element
import scalatags.JsDom.all.{`class`, attr, div, id, _}

case class ModalDialog(openText: String, okText: String = "Ok", buttonClassName : String = "btn btn-secondary") {

  val messages: MessageAlerts = MessageAlerts.info()

  private val modalId = uniqueId()
  val openButton = button(`type` := "button",
    `class` := buttonClassName,
    attr("data-toggle") := "modal",
    attr("data-target") := s"#$modalId")(openText).render

  lazy val okButton = button(`type` := "button", `class` := "btn btn-secondary", attr("data-dismiss") := "modal")(okText).render

  def render(title: String, body: Element, modalClassName : String = "modal-dialog modal-lg"): Div = {
    div(`class` := "modal fade", `id` := modalId)(
      div(`class` := modalClassName)(
        div(`class` := "modal-content")(
          div(`class` := "modal-header")(
            h4(`class` := "modal-title")(title),
            button(`type` := "button", `class` := "close", attr("data-dismiss") := "modal")(raw("&times;"))
          ),
          div(`class` := "modal-body")(body),
          div(`class` := "modal-footer")(okButton, messages.messageDiv)
        )
      )
    ).render
  }
  def renderDiv(title: String, body: Element): Div = {
    div(
      div(`class` := "container")(
        openButton
      ),
      render(title, body)
    ).render
  }
}
