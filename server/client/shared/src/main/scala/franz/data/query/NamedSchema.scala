package franz.data.query

import donovan.implicits._
import donovan.json.TypeNode
import io.circe.syntax._
import io.circe.{Encoder, Json}

import scala.reflect.ClassTag

final case class NamedSchema(typeName: String, schema: TypeNode) {
  override def toString: String = schema.flatten.mkString(s"$typeName\n", "\n", "\n")
}

object NamedSchema {
  def apply[A: ClassTag : Encoder](value: A): NamedSchema = {
    apply(implicitly[ClassTag[A]].runtimeClass.getSimpleName, value.asJson)
  }

  def apply(name: String, json: Json): NamedSchema = new NamedSchema(name, json.schema)

  implicit val encoder = io.circe.generic.semiauto.deriveEncoder[NamedSchema]
  implicit val decoder = io.circe.generic.semiauto.deriveDecoder[NamedSchema]
}
