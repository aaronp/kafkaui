package franz.errors

case class MissingPermissions(requiredPermission : String)

object MissingPermissions {
  implicit val encoder = io.circe.generic.semiauto.deriveEncoder[MissingPermissions]
  implicit val decoder = io.circe.generic.semiauto.deriveDecoder[MissingPermissions]
}
