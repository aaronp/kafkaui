package franz.client.bootstrap

import cats.data.{NonEmptyChain, Validated}
import cats.implicits._
import donovan.json.JPath
import franz.client.js.JsonAsString
import io.circe.Json
import scalatags.JsDom.all.{form, _}

object InputForm {
  def apply(first: (String, Component), theRest: (String, Component)*): InputForm = new InputForm(first :: theRest.toList)
}

/**
 * A list of components pairs with some json type
 *
 * @param componentsByJPath
 */
case class InputForm(componentsByJPath: List[(String, Component)]) extends Component {

  def paths = componentsByJPath.map(_._1)

  def asCells: List[ListItem.Cell] = paths.map { key =>
    def valueOf(json: Json) = {
      val opt = json.asObject.map(obj => obj(key).getOrElse(Json.fromString(s"obj doesn't have value for '$key' in $json")))
      val found = opt.getOrElse(Json.fromString(s"json isn't an object, can't get '$key' in $json"))
      JsonAsString(found)
    }

    (key, valueOf)
  }

  override def render = {
    form()(
      componentsByJPath.map(_._2.render)
    ).render
  }

  override def validated(): Validated[NonEmptyChain[Component.Error], Json] = {
    val pears: List[Validated[NonEmptyChain[Component.Error], (String, Json)]] = componentsByJPath.map {
      case (key, c) =>
        c.validated().map { json =>
          (key, json)
        }
    }

    pears.sequence.map { keyValues: List[(String, Json)] =>
      Json.obj(keyValues: _*)
    }
  }

  override def update(value: Json): Unit = {
    componentsByJPath.foreach {
      case (path, c) =>
        val nestedValue = JPath(path).apply(value).getOrElse(Json.Null)
        c.update(nestedValue)
    }
  }
}
