package franz.firestore

import java.util
import java.util.Collections

import io.circe.syntax._
import io.circe.{Encoder, Json, JsonObject}

import scala.jdk.CollectionConverters._
import scala.util.Try

object JsonToMap {

  def asJson(jValue: Any): Json = {
    jValue match {
      case null => Json.Null
      case value: util.Collection[_] => Json.fromValues(value.asScala.map(asJson))
      case value: java.lang.Boolean => Json.fromBoolean(value)
      case value: java.lang.Double => Json.fromDoubleOrNull(value)
      case value: String => Json.fromString(value)
      case map: util.Map[String, _] => Json.fromFields(map.asScala.view.mapValues(asJson))
      case other => sys.error(s"Couldn't map '${other}' of type ${Try(other.getClass).map(_.toString).getOrElse("")}")
    }

  }

  def from(jMap: java.util.Map[String, AnyRef]): Json = asJson(jMap)


  def apply[A: Encoder](value: A): Object = toAny(value.asJson)

  def toMap[A: Encoder](value: A): util.Map[String, Object] = toMap(value.asJson)

  def toMap(json: Json): util.Map[String, Object] = Collections.singletonMap("data", toAny(json))

  def toAny(json: Json): Object = {
    json.fold(
      null,
      (b: Boolean) => java.lang.Boolean.valueOf(b),
      d => java.lang.Double.valueOf(d.toDouble),
      s => s,
      array => array.map(toAny).asJava,
      obj => forObj(obj)
    )
  }

  private def forObj(json: JsonObject): util.Map[String, Object] = {
    json.toMap.view.mapValues(toAny).toMap.asJava
  }

}
