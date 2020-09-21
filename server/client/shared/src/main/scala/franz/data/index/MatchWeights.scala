package franz.data.index

import cats.effect.Sync
import franz.data.crud.CrudServices
import franz.rest.Swagger
import io.circe.Json

/**
 * Some settings for applying weighed criteria based on a property (field)
 *
 * @param weightByField
 */
final case class MatchWeights(weightByField: Map[String, Double])

object MatchWeights {
  val Namespace = "matchweights"

  val empty = new MatchWeights(Map.empty)

  def of(entries: (String, Double)*) = new MatchWeights(entries.toMap)

  implicit val encoder = io.circe.generic.semiauto.deriveEncoder[MatchWeights]
  implicit val decoder = io.circe.generic.semiauto.deriveDecoder[MatchWeights]

  def client[F[_] : Sync](restClient: Swagger.Client[F, Json]): CrudServices[F, MatchWeights] = {
    CrudServices.client[F, MatchWeights](MatchWeights.Namespace, restClient)
  }
}
