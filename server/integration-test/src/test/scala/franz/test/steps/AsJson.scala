package franz.test.steps

import franz.data.JsonAtPath
import io.circe.{Json, JsonObject}
import io.circe.parser.decode

/**
 * Some relaxed json conversion for the cucumber tests which ignore fields like timestamps and JWT tokens
 */
object AsJson {

  // util for parsing json and removing troublesome fields (e.g. timestamps) we don't want to compare
  private val replaceJWTInJson: Json => Json = {
    val JwtR = "[A-Za-z0-9_=-]+\\.[A-Za-z0-9_=-]+\\.[A-Za-z0-9_=-]+".r
    JsonAtPath.mapStrings {
      case JwtR() => "J.W.T"
    }
  }

  def apply(jsonBody: Either[String, String]): Json = {
    jsonBody match {
      case Left(bdy) => apply(bdy)
      case Right(bdy) => apply(bdy)
    }
  }


  // some tests fail 'cause the json returns unsorted arrays
  def sortArrays(json : Json) : Json = {
    def arrSort(arr : Vector[Json]): Json = {
      Json.arr(arr.map(sortArrays).sortBy(_.noSpaces):_*)
    }
    def recurse(obj : JsonObject) = Json.fromJsonObject(obj.mapValues(sortArrays))
    json.arrayOrObject[Json](json, arrSort, recurse)
  }
  def apply(jsonBody: String): Json = {
    val json = decode[Json](jsonBody).toTry.get

    // clear out timestamps
    val removedIAT = JsonAtPath.removeNested("iat")(json)
    val removedTimestamp: Json = JsonAtPath.removeNested("createdEpochMillis")(removedIAT)

    // and replace specific tokens
    replaceJWTInJson(removedTimestamp)
  }
}
