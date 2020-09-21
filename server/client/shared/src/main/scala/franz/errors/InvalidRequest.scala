package franz.errors

final case class InvalidRequest(invalidRequest: String)

object InvalidRequest {
  implicit val encoder = io.circe.generic.semiauto.deriveEncoder[InvalidRequest]
  implicit val decoder = io.circe.generic.semiauto.deriveDecoder[InvalidRequest]
}
