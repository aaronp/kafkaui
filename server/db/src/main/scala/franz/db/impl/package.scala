package franz.db

import franz.data.collectionNameFor
import franz.data.VersionedRecord
import io.circe.Decoder
import mongo4m.BsonUtil
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.{Document, MongoCollection}

import scala.reflect.ClassTag
import scala.util.{Failure, Try}

package object impl {

  type DocCollection = MongoCollection[Document]

  private[impl] def versionedNameFor[A: ClassTag]: String = versionedNameFor(collectionNameFor[A])

  private[impl] def versionedNameFor(collection: String): String = {
    if (collection.endsWith("Versions")) collection else s"${collection}Versions"
  }

  private[impl] def schemasNameFor[A: ClassTag]: String = schemasNameFor(collectionNameFor[A])

  private[impl] def schemasNameFor(collection: String): String = {
    if (collection.endsWith("Schemas")) collection else s"${collection}Schemas"
  }

  private[impl] def latestNameFor[A: ClassTag]: String = latestNameFor(collectionNameFor[A])

  private[impl] def latestNameFor(collection: String): String = {
    if (collection.endsWith("Latest")) collection else s"${collection}Latest"
  }

  private[impl] def docAsVersionedA[A: Decoder : ClassTag](doc: BsonDocument): Try[VersionedRecord[A]] = {
    val tri = BsonUtil.fromBson(doc).flatMap { json =>

      json.as[VersionedRecord[A]].toTry match {
        case Failure(err) =>
          val aType = implicitly[ClassTag[A]].runtimeClass.getName
          Failure(new Exception(s"Couldn't unmarshal ${json.spaces2} as $aType due to '${err.getMessage}'", err))
        case ok => ok
      }
    }
    tri
  }
}
