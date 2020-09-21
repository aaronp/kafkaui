package franz.client.bootstrap

import cats.syntax.validated._
import franz.client.js._
import io.circe.Json
import scalatags.JsDom.all.{`class`, `for`, `type`, div, input, label, _}

/**
 * @param labelValue
 * @param placeholderValue
 */
case class TextInput(labelValue: String,
                     placeholderValue: String = "",
                     initialValue: String = "",
                     divClass: String = "form-group row",
                     inputClass: String = "form-control form-control-lg",
                     labelClass: String = "col-sm-2 col-form-label",
                     inputDivClass: String = "col-sm-5"
                    ) extends Component {
  val textInputControl = input(`class` := inputClass, `type` := "text", placeholder := placeholderValue, id := uniqueId(), value := initialValue)().render

  override def render = {
    div(`class` := divClass)(
      label(`class` := labelClass, `for` := textInputControl.id)(labelValue),
      div(`class` := inputDivClass)(textInputControl)
    ).render
  }

  override def update(value: Json): Unit = {
    val newValue = if (value.isNull) {
      initialValue
    } else {
      JsonAsString(value)
    }
    textInputControl.value = newValue
  }

  override def validated() = jsonValue().validNec

  def currentText() = textInputControl.value

  def jsonValue(): Json = Json.fromString(currentText())
}
