package franz.client.js

import cats.data.{NonEmptyChain, Validated, ValidatedNec}
import franz.client.bootstrap.Component
import io.circe.Json
import cats.syntax.validated._

object JsonAsInt {

  def apply(value : Json): Validated[NonEmptyChain[Component.Error], Int] = {
    def bad(typ: String): ValidatedNec[Component.Error, Int] = {
      Component.Error(s"${JsonAsString(value)} was $typ when it gone done shoulda been numeric").invalidNec[Int]
    }

    def optAsValid(str : String, intOpt : Option[Int]): ValidatedNec[Component.Error, Int] = intOpt match {
      case None => Component.Error(s"${JsonAsString(value)} was '$str' when it gone done shoulda been numeric").invalidNec[Int]
      case Some(n) => n.validNec
    }

    value.fold[Validated[NonEmptyChain[Component.Error], Int]](
      bad("null"),
      _ => bad("a boolean"),
      n => optAsValid(n.toString, n.toInt),
      str => optAsValid(str, str.toIntOption),
      _ => bad("a boolean"),
      _ => bad("a boolean"),
    )
  }
}
