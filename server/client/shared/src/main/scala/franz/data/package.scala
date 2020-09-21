package franz

import cats.syntax.option._
import franz.data.crud.InsertResponse
import io.circe.Json

import scala.reflect.ClassTag

package object data {

  type CollectionName = String
  type QueryString = String
  type IndexValue = String
  type ValuePath = Seq[String]
  type Version = Int
  type Id = String

  type VersionedJson = VersionedRecord[Json]
  type VersionedResponse[A] = InsertResponse[VersionedRecord[A]]
  type VersionedJsonResponse = VersionedResponse[Json]

  /** @tparam A
   * @return the collection (table) name for the given type
   */
  def collectionNameFor[A: ClassTag]: String = implicitly[ClassTag[A]].runtimeClass.getSimpleName.filter(_.isLetter).toLowerCase()

  def versionedNameFor[A: ClassTag]: String = versionedNameFor(collectionNameFor[A])

  def versionedNameFor(collection: String): String = {
    if (collection.endsWith("Versions")) collection else s"${collection}Versions"
  }

  def schemasNameFor[A: ClassTag]: String = schemasNameFor(collectionNameFor[A])

  def schemasNameFor(collection: String): String = {
    if (collection.endsWith("Schemas")) collection else s"${collection}Schemas"
  }

  def latestNameFor[A: ClassTag]: String = latestNameFor(collectionNameFor[A])

  def latestNameFor(collection: String): String = {
    if (collection.endsWith("Latest")) collection else s"${collection}Latest"
  }

  object BaseCollection {

    val LatestR = "(.*)Latest".r
    val VersionsR = "(.*)Versions".r

    def unapply(name: CollectionName): Option[CollectionName] = {
      name match {
        case LatestR(base) => base.some
        case VersionsR(base) => base.some
        case base => base.some
      }

    }

    def distinct(names: Iterable[CollectionName]) = {
      val bases = names.collect {
        case BaseCollection(name) => name
      }
      bases.toList.distinct.sorted
    }
  }

}
