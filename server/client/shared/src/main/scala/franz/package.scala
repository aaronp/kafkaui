
/**
 *
 * Advanced matching (querying, writing association results) will be covered as a different application.
 *
 * ==============================================================================
 * ==================================== DONE ====================================
 * ==============================================================================
 * Therefore we have the equivalent of:
 * {{{
 *   POST /index/<collection>/<name>  # create a compound index to use when writing records
 *   DELETE /index/<collection>/<name>  # remove a compound index
 *   GET /index/<name>  # read an index
 *
 *   POST /data/<collection>  # send a versioned record
 *   GET /data/<collection>/<id>  # get the latest version record
 *   GET /data/<collection>/<id>?version=v  # get version v of the record
 *   DELETE /data/<collection>/<id>  # delete record
 * }}}
 *
 * Differences:
 * {{{
 *   GET /diff/<collection>/<lhsId>/<rhsId>?lhsVersion=1&rhsVersion=2 # diff two records w/ optional IDs
 *   GET /diff/<collection>/<id>?firstVersion=1&secondVersion=2 # diff a records between two versions
 *   POST /diff # POST difference, perhaps between collections, ids and versions
 * }}}
 *
 * ==============================================================================
 * ==================================== TODO ====================================
 * ==============================================================================
 *
 * Matching:
 * Using some weights and saved queries/joins, find matches for a record
 * {{{
 *   GET  /match/<collection>/<id> # return the match results for a particular record
 *   POST /match # returns matches for a POSTed record (and weights)
 *
 *   POST /weights # save some match weights to use
 *   DELETE /weights/<id> # remove match weights
 *
 * }}}
 *
 * Queries:
 * {{{
 *   GET /query/<collection>?from=XXX&limit=YYY&foo.bar=7  # list YYY records where foo.bar is 7
 *   POST /query/<collection> # a query in a post request
 *
 *    POST /queries      # save some fixed query to run on matches
 *    DELETE /queries/<id> # remove match weights
 *
 *   POST /query/<collection>/<id> # create a named query
 *   GET /query/<collection>/<id> # run a named query
 * }}}
 *
 * Covered in other "applications"
 *
 * Saved Associations:
 * Keep track of matches for particular records
 * {{{
 *   GET  /association/<collection>/<id> # gets the latest association for a particular record
 *   POST /association/<collection>/<id> # sets an association for a record
 * }}}
 *
 *
 *
 * Schemas (see [[franz.data.query.NamedSchema]] :
 * {{{
 *   GET /schema/<collection> # list the schemas for a collection
 *   GET /schema/<collection>/<id>  # diff a records between two versions
 * }}}
 */
package object franz {

  val DataNamespace = "data"

  val WeightsNamespace = "matchweights"
}
