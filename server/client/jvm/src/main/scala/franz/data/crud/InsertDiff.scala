package franz.data.crud

import donovan.json._
import franz.data.VersionedJsonResponse
import io.circe.Json

object InsertDiff {

  def diff(before: Json, after: Json): JsonDiff = {
    donovan.json.JsonDiff(before, after)
  }

  def apply(response: VersionedJsonResponse): Option[JsonDiff] = {
    ???
//    response match {
//      case InsertSuccess(_, after) => Option(diff(before.data, after.data))
//      case _ => None
//    }
  }
}
