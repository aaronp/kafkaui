package franz.data

import io.circe.{Json, JsonObject}

/**
 * A means to create some json at a particular path
 */
object JsonAtPath {

  def mapStrings(thunkP: PartialFunction[String, String]): Json => Json = {
    def thunk(s: String) = thunkP.lift(s).getOrElse(s)

    (json: Json) => {
      json.mapArray(_.map(mapStrings(thunkP))).mapObject(_.mapValues(mapStrings(thunkP))).mapString(thunk)
    }
  }

  def removeNested(path: String)(json: Json): Json = {
    json.mapObject { obj: JsonObject =>
      obj.mapValues(removeNested(path)).remove(path)
    }.mapArray(_.map(removeNested(path)))
  }

  def apply(path: String, value: String): Json = JsonAtPath(path, Json.fromString(value))

  def apply(path: String, value: Json): Json = JsonAtPath(path.split(".", -1).toIndexedSeq, value)

  def apply(path: Seq[String], value: Json): Json = {
    path.foldRight(value) {
      case (next, j) => Json.obj(next -> j)
    }
  }

  def nonEmpty(json: Json) = !isEmpty(json)

  def asString(json: Json): String = {
    json.asString.getOrElse(json.noSpaces)
  }

  def isEmpty(json: Json) = {
    json.isNull || json.asArray.exists(_.isEmpty) || json.asString.exists(_.trim.isEmpty) || json.asObject.exists(_.isEmpty)
  }
}
