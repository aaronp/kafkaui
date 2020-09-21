package franz.app

import cats.{ApplicativeError, Monad}
import franz.UserApi
import franz.data.crud.{CrudServicesAnyCollection, Search}
import franz.data.diff.{Diff, DiffRest}
import franz.data.index.{AssociationQueries, CompoundIndex, IndexedValue, MatchWeights}
import franz.data.{CollectionName, QueryRange}
import franz.rest.Swagger
import franz.users.{AdminApi, UserHealth}
import io.circe.Json

/**
 * TODO - move AppServices from rest into here and just use that
 *
 * @param jsonClient
 * @param monad
 * @param appErr
 * @tparam F
 */
case class AppClient[F[_]](jsonClient: Swagger.Client[F, Json])(implicit monad: Monad[F], appErr: ApplicativeError[F, Throwable]) {
  val associationsClient: AssociationQueries[F] = AssociationQueries.client(jsonClient)

  def readIndex(value: String): F[Option[IndexedValue]] = associationsClient.readIndex.read(value)

  val compoundIndexClient: CompoundIndex.DSL[F] = CompoundIndex.client(jsonClient)
  val userApi: UserApi[F] = UserApi.client(jsonClient)
  val adminClient: AdminApi[F] = AdminApi.client(jsonClient)
  val healthClient: UserHealth.Client[F, Json] = UserHealth.Client(jsonClient)
  val diffClient: Diff.Service[F] = DiffRest.client(jsonClient)
  val crudClient = CrudServicesAnyCollection.client[F](franz.DataNamespace, jsonClient)

  //val weightsClient: CrudServices[F, MatchWeights] = CrudServices.client[F, MatchWeights](WeightsNamespace, jsonClient)

  val searchClient = Search.client[F](jsonClient)

  def listCollections(range: QueryRange): F[List[CollectionName]] = crudClient.listService.list(range)
}
