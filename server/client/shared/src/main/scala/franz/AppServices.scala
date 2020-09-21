package franz

import cats.effect.Sync
import cats.{Parallel, ~>}
import franz.data.RecordCoords
import franz.data.crud.{CrudServices, CrudServicesAnyCollection, CrudServicesInMemory, Search}
import franz.data.index.{AssociationQueries, CompoundIndex, IndexingCrud, MatchWeights}

/**
 * Services used by our application - reading/writing data, matching records, reading/writing new compound indices, and reading/writing match weights.
 *
 * These services are used as the back-end for our REST routes as well as the clients of our services - bonus!
 *
 * @param crudServices   a means to write down data against arbitrary collections
 * @param matchServices  the services for matching
 * @param weights        crud for match weights
 * @param searchServices a generic 'search by text criteria' service
 * @param indicesCrud    a means to create new indices on data (e.g. create 'fullname' from jpath 'data.first' + 'data.last')
 * @tparam F
 */
case class AppServices[F[_]](crudServices: CrudServicesAnyCollection[F],
                             matchServices: AssociationQueries[F],
                             weights: CrudServices[F, MatchWeights],
                             searchServices: Search.Service[F],
                             indicesCrud: CrudServices[F, Seq[CompoundIndex]]) {
  def mapK[G[_]](implicit ev: F ~> G): AppServices[G] = {
    new AppServices[G](
      crudServices.mapK[G],
      matchServices.mapK[G],
      weights.mapK[G],
      searchServices.mapK[G],
      indicesCrud.mapK[G]
    )
  }
}

object AppServices {

  def inMemory[F[_] : Sync : Parallel](requiredContiguousVersions: Boolean, tooManyValuesThreshold: Int): AppServices[F] = {
    val indexer: IndexingCrud[F] = IndexingCrud.inMemory[F](requiredContiguousVersions, tooManyValuesThreshold)
    val weightCrud = CrudServicesInMemory[F, MatchWeights](true).services
    val indicesCrud = CrudServicesInMemory[F, Seq[CompoundIndex]](true).services

    // TODO - the search here only searches on ID
    val search = Search.liftF { request =>
      val readResponseF = indexer.crudServices.readService.read(RecordCoords.latest(request.collection, request.queryString))
      import cats.syntax.functor._
      readResponseF.map {
        case None => Search.Response(0, Nil, 0)
        case Some(found) => Search.Response(1, List(found), 1)
      }
    }

    apply[F](indexer, weightCrud, indicesCrud, search)
  }

  def apply[F[_] : Sync : Parallel](indexer: IndexingCrud[F],
                                    weightCrud: CrudServices[F, MatchWeights],
                                    indicesCrud: CrudServices[F, Seq[CompoundIndex]],
                                    search: Search.Service[F]): AppServices[F] = {
    new AppServices(
      indexer.indexingCrud,
      indexer.associationQueries,
      weightCrud,
      search,
      indicesCrud
    )
  }
}
