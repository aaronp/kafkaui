package franz.data

import donovan.json.ValuesByPath
import io.circe.{Encoder, Json}
import franz.data.JsonAtPath._

package object index {

  def referencesFor[A: Encoder](collection: CollectionName, entity: VersionedRecord[A]): List[(IndexValue, ReferenceToValue)] = {
    val view = ValuesByPath(entity.data).view.collect {
      case (path, json: Json) if nonEmpty(json) => asString(json) -> ReferenceToValue(collection, path, entity.id, entity.version)
    }
    view.toList
  }

  def valuesFor[A: Encoder](entity: A): List[(ValuePath, String)] = {
    val view = ValuesByPath(entity).view.collect {
      case (path, json: Json) if nonEmpty(json) => (path, asString(json))
    }
    view.toList
  }
}
