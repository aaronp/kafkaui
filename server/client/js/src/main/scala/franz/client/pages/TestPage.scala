package franz.client.pages

import franz.client.bootstrap._
import franz.client.js.{HtmlUtils, MessageAlerts, _}
import franz.client.pages.compoundindex._
import scalatags.JsDom.all.{`class`, div, _}

import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

@JSExportTopLevel("TestPage")
object TestPage extends HtmlUtils {

  val alert = MessageAlerts.info(MessageAlerts.DefaultDuration * 4)

  @JSExport
  def render(mainContainerId: String): Unit = {
    $(mainContainerId).innerHTML = ""
    $(mainContainerId).appendChild(formContainer)
  }

  private def formContainer = {
    if (true) {
      compoundIndexTest
    } else {
      tabsTest
      mergeValuesTest
      // other stuff we can test:
      stemOpsTest
      ModalDialog("open the dialog").renderDiv("Example", div("Your content here").render)
      expandValuesPageTest
    }
  }

  def expandValuesPageTest = {
    val container = div(id := "foo")().render
    val stack = NavStack(container.id)
    val testBtn = a(`class` := "btn btn-link")("Test").render
    val expand = ExpandValuesComponent(stack)
    testBtn.onclick = e => {
      e.cancel()
      alert.pushMessage(expand.validatedIndex().toString)
    }

    stack.push("Home") {
      TextInput("Some Home thing...")
    }
    stack.push("Expand Values")(expand)
    div(
      stack.renderDiv(),
      container,
      alert.messageDiv,
      testBtn
    ).render
  }

  def compoundIndexTest = {
    val compound = CompoundIndexPage("cal", CompoundIndexPage.Service.Stub(), true)

    div(
      compound.render(),
      alert.messageDiv
    ).render
  }

  def tabsTest = {
    //    Tabs().render()
    val testBtn = a(`class` := "btn btn-link")("Test").render


    val tabs = ChoicePanel.horizontal(
      "A" -> Component.text("alpha"),
      "B" -> Component.text("beta"),
      "C" -> Component.text("gamma")
    )
    val vert = ChoicePanel.vertical(
      "D" -> Component.text("delta"),
      "E" -> Component.text("epsilon"),
      "F" -> Component.text("zeta")
    )
    testBtn.onclick = e => {
      e.cancel()
      alert.pushMessage(s"TABS: ${tabs.validated()}")
      alert.pushMessage(s"VERTICAL: ${vert.validated()}")
    }
    div(tabs.render(), vert.render(), div(alert.messageDiv), div(testBtn)).render
  }

  def mergeValuesTest = {

    val testBtn = a(`class` := "btn btn-link")("Test").render
    val underTest = MergeValuesComponent()
    testBtn.onclick = e => {
      e.cancel()
      alert.pushMessage(underTest.validatedIndex().toString)
    }

    div(
      underTest.render(),
      alert.messageDiv,
      testBtn
    ).render
  }

  def mergeValuesPathTest = {

    val testBtn = a(`class` := "btn btn-link")("Test").render
    val underTest = MergeValuesPathComponent()
    testBtn.onclick = e => {
      e.cancel()
      alert.pushMessage(underTest.validatedSelectPath().toString)
    }

    div(
      underTest.render(),
      alert.messageDiv,
      testBtn
    ).render
  }

  private def stemOpsTest = {
    val cb = StemOpComponent()
    val testBtn = a(`class` := "btn btn-link")("Test").render
    testBtn.onclick = e => {
      e.cancel()
      alert.pushMessage(cb.validatedTyped().toString)
    }

    div(cb.render(), div(alert.messageDiv), div(testBtn)).render
  }

}
