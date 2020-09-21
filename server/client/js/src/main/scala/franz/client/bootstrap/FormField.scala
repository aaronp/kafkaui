package franz.client.bootstrap

import cats.data.{NonEmptyChain, Validated, ValidatedNec}
import cats.implicits._
import io.circe.Json
import io.circe.parser._
import org.scalajs.dom.html.{Input, TextArea}

/**
 * Not a component, but just a case class representation of the current state of some field.
 *
 * The intention is to be able to put generic "go to error" and validation rules around some simple data types,
 * though this could be a pain in the arse
 *
 * @param id
 * @param value
 */
case class FormField(id: String, value: String) {
  def orElse(other: => String) = value.trim match {
    case "" => copy(value = other)
    case _ => this
  }

  def error(msg: String) = FormField.Error(this, msg)

  def validJsonComponent: Validated[NonEmptyChain[Component.Error], Json] = {
    validJson.leftMap(Component.Error.apply)
  }
  def validJson: ValidatedNec[FormField.Error, Json] = {
    value.trim match {
      case "" => error("Json content is empty").invalidNec
      case "null" => Json.Null.validNec[FormField.Error]
      case jsonStr =>
        decode[Json](jsonStr) match {
          case Left(errorMsg) => error(s"Couldn't parse json: $errorMsg").invalidNec
          case Right(json) => json.validNec[FormField.Error]
        }
    }
  }

  def noWhitespace(label: String): Validated[NonEmptyChain[FormField.Error], String] = {
    value.trim match {
      case name if name.exists(_.isWhitespace) => error(s"$label cannot contain whitespace").invalidNec
      case _ => nonEmpty(label)
    }
  }

  def numeric(label: String): Validated[NonEmptyChain[FormField.Error], Int] = {
    value.toIntOption match {
      case None => error(s"$label must be numeric").invalidNec
      case Some(x) => x.validNec
    }
  }
  def nonEmpty(label: String): Validated[NonEmptyChain[FormField.Error], String] = {
    value.trim match {
      case "" => error(s"$label cannot be empty").invalidNec
      case name => name.validNec
    }
  }
}

object FormField {
  def apply(input: TextArea) = new FormField(input.id, input.value)

  def apply(input: Input) = new FormField(input.id, input.value)

  case class Error(field: FormField, message: String) {
    def asComponentError = Component.Error(this)
  }
}
