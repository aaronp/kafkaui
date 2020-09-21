package franz.client.bootstrap

import io.circe.Json
import org.scalajs.dom.html.TextArea
import scalatags.JsDom.all.{`class`, rows, textarea, _}

case class TextAreaInput(numRows: Int = 10) extends Component {
  override val render: TextArea = textarea(`class` := "form-control", rows := numRows)("").render

  override def validated = FormField(render).validJsonComponent

  override def update(value: Json): Unit = render.value = value.spaces2
}
