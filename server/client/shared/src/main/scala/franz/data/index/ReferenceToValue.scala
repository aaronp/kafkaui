package franz.data.index

import franz.data._


/**
 *
 * @param collection
 * @param id
 * @param version
 */
case class ReferenceToValue(collection: CollectionName, path: ValuePath, id: String, version: Int) {
  override def toString = path.mkString(s"$collection:", ".", s" => $id@$version")

  def replaces(other: ReferenceToValue): Boolean = matchesKey(other) && version > other.version

  def matches(otherColl: CollectionName, otherId: String, otherVersion: Int): Boolean = {
    otherColl == collection &&
      otherId == id &&
      otherVersion == version
  }

  def matchesKey(other: ReferenceToValue): Boolean = {
    collection == other.collection && id == other.id
  }

  def matchesKeyAndPath(other: ReferenceToValue): Boolean = {
    matchesKey(other) && path == other.path
  }

  def key = ReferenceToValue.RecordKey(collection, id)

  def score(matchWeights: MatchWeights): Double = {
    val pathKey = path.mkString(".")
    matchWeights.weightByField.getOrElse(pathKey, 1.0)
  }
}

object ReferenceToValue {
  implicit val encoder = io.circe.generic.semiauto.deriveEncoder[ReferenceToValue]
  implicit val decoder = io.circe.generic.semiauto.deriveDecoder[ReferenceToValue]

  implicit object RecordIsVersioned extends IsVersioned[ReferenceToValue] {
    override def versionFor(value: ReferenceToValue): Int = value.version
  }

  final case class RecordKey(collection: CollectionName, id: String)

  object RecordKey {
    implicit val encoder = io.circe.generic.semiauto.deriveEncoder[RecordKey]
    implicit val decoder = io.circe.generic.semiauto.deriveDecoder[RecordKey]
  }

}
