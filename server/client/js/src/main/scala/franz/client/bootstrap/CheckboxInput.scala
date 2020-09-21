package franz.client.bootstrap

import cats.syntax.validated._
import io.circe.Json
import scalatags.JsDom.all.{`class`, `for`, `type`, div, input, label, _}

/**
 * @param labelValue
 * @param placeholderValue
 */
case class CheckboxInput(labelValue: String,
                         placeholderValue: String = "",
                         inputClass: String = "form-control",
                         labelClass: String = "col-lg-5 col-form-label",
                         inputDivClass: String = "col-sm-2"
                        ) extends Component {
  val checkboxControl = input(`class` := inputClass, `type` := "checkbox", `placeholder` := placeholderValue)().render

  override def render = {
    div(`class` := "form-group row")(
      label(`class` := labelClass, `for` := checkboxControl.id)(labelValue),
      div(`class` := inputDivClass)(checkboxControl)
    ).render
  }

  override def update(value: Json): Unit = {
    checkboxControl.checked = value.asBoolean.exists(identity)
  }

  override def validated() = jsonValue().validNec

  def isChecked() = checkboxControl.checked
  def check(checked : Boolean) = checkboxControl.checked = checked

  def jsonValue(): Json = {
    if (checkboxControl.checked) Json.True else Json.False
  }
}
