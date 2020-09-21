package franz.client.pages.compoundindex

import cats.data.{NonEmptyChain, Validated}
import cats.implicits._
import franz.client.bootstrap.{Component, InputForm, NavStack}
import franz.data.index.ExpandValues
import io.circe.Json
import io.circe.syntax._

/**
 * The component for ExpandValues
 */
case class ExpandValuesComponent(nav: NavStack) extends Component.Delegate {

  val fromPath = fromPathTextInput
  val targetPath = targetPathTextInput
  val splitOps = SplitOpComponent(nav)


  underlying = InputForm(
    "fromPath" -> fromPath,
    "targetPath" -> targetPath,
    "splitOperation" -> splitOps
  )

  override def validated(): Validated[NonEmptyChain[Component.Error], Json] = {
    validatedIndex().map(_.asJson)
  }

  def validatedIndex(): Validated[NonEmptyChain[Component.Error], ExpandValues] = {
    val splitOptV = splitOps.splitOpComponent.validatedTyped()

    (validPath(fromPath.currentText()), validPath(targetPath.currentText()), splitOptV).mapN {
      case (f, t, s) => ExpandValues(f, t, s)
    }
  }

  override def update(value: Json): Unit = {
    value.as[ExpandValues].toOption match {
      case None =>
        fromPath.update(value)
        targetPath.update(value)
        splitOps.update(value)
      case Some(ok) =>
        fromPath.update(ok.fromPath.mkString(".").asJson)
        targetPath.update(ok.targetPath.mkString(".").asJson)
        splitOps.update(ok.splitOperation.asJson)
    }
  }
}
