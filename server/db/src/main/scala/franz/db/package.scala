package franz

/**
 * We want to be able to scale this horizontally, which means doing the query logic on document insertion as opposed to log tailing
 * (otherwise we would have to somehow get the log-tail instances to communicate between them which were running)
 *
 * To track associations between collections, we have the following tables:
 *
 * $ <collection>Latest - the latest, up-to-date version of some [[VersionedRecord]]s
 * $ <collection>Versions - a full history of each version of a collection using contiguous version numbers
 * $ <collection>MatchQueries - a collection of user-defined match criteria between two collections (LHS and RHS)
 * $ <collection>Schemas - a collection of schema versions kept up-to-date when we write down new records
 * $ <collection>Associations - the results of running user [[InsertTrigger]]s against two collections.
 * This collection is based on the 'left hand side' collection name of the UserMatchQuery.
 *
 * Achieved Milestones:
 * $ be able to insert into the *Versions collection, ensuring append-only and contiguous version numbers are allowed (e.g. no clobber) of versions
 * $ ensure Version updates keep the base <collection> up-to-date
 * $ execute queries based on [[InsertTrigger]] and prove the results
 * $ CRUD on <collection>MatchQueries
 * $ derive and insert/query schemas
 */
package object db {
}
