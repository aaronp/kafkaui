package franz.data.query

import franz.data.{QueryRange, RecordCoords, VersionedJson}

/**
 * Support match queries and general searches:
 *
 * {{{
 *   POST /rest/query/<collection>                           # send either search criteria which can be saved under a name
 *   GET /rest/query/<collection>/<name>?limit=100&from=123  # run the named query
 *
 *   GET /rest/match/<collection>/<id>?version=latest  # find matches for a particular record
 *   GET /rest/match/<collection>?foo.bar=xyz&meh=true  # find matches for particular index values
 *   GET /rest/match?foo.bar=xyz&meh=true  # find matches for particular index values
 * }}}
 * @tparam F
 */
trait Queries[F[_]] {

  def queriesService: Queries.Service[F]
}

object Queries {

  trait Service[F[_]] {
    def query(request: Request): F[QueryResponse[request.Response]]
  }

  sealed trait Request {
    type Response
  }

  /**
   * Find the best matching records
   * @param record
   * @param limit
   */
  case class FindAssociatedRecords(record: RecordCoords, limit: QueryRange) extends Request {
    override type Response = VersionedJson
  }
}
