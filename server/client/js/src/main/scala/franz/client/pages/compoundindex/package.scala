package franz.client.pages

import cats.data.{NonEmptyChain, Validated}
import cats.implicits._
import franz.client.bootstrap.{Component, TextInput}
import franz.data.index.SplitOp

/**
 * The UI for these things gets hairy.
 *
 * There are two types of [[franz.data.index.CompoundIndex]], which can have nesting, union types, etc.
 *
 */
package object compoundindex {

  def validPath(str: String): Validated[NonEmptyChain[Component.Error], List[String]] = {
    str.split("\\.", -1).toList match {
      case Nil => Component.Error("Path must not be empty").invalidNec
      case path if SplitOp.isValidPath(path) =>
        path.validNec
      case _ => Component.Error(s"Invalid path '${str}'").invalidNec
    }
  }

  def fromPathTextInput = TextInput("From Path:",
    placeholderValue = "e.g. user.contactDetails.name",
    labelClass = "col-lg-2 col-form-label",
    inputDivClass = "col-lg-10")

  def targetPathTextInput = TextInput("Target Path:",
    placeholderValue = "e.g. user.contactDetails.names",
    labelClass = "col-lg-2 col-form-label",
    inputDivClass = "col-lg-10")

}
