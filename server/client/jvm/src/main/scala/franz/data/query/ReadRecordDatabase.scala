package franz.data.query

import cats.{Applicative, Monad}
import franz.data.QueryRange
import franz.data.crud.{ListRecords, ReadRecord}
import io.circe.{Encoder, Json}


/**
 * This is nifty - allow generic 'MatchCriteria' against any type A so long as there is an Encoder for it.
 *
 *
 * This object contains functions which would give you the equivalent of a full-table-scan database, offering
 * O(n) performance against a fixed set of data
 *
 */
object ReadRecordDatabase {

  /** @param data the data represented by this 'database'
   * @tparam F the effect type
   * @tparam A the data type
   * @return a read service from a fixed set of data
   */
  def apply[F[_] : Applicative, A: Encoder](data: Seq[A]): ReadRecord.Service[F, (MatchCriteria, Json), Seq[A]] = {
    ReadRecord.lift[F, (MatchCriteria, Json), Seq[A]] {
      case (criteria, record) =>
        val filter: A => Boolean = JsonFilterForCriteria.createFilterFor(criteria, record)
        data.filter(filter)
    }
  }

  /**
   * Like the 'apply' variant, but created from a generic list service - essentially just filtering all the data
   *
   * @param data some generic list service
   * @tparam F
   * @tparam A
   * @return a read service based on *all* the results of the list service
   */
  def fromList[F[_] : Monad, A: Encoder](data: ListRecords.Service[F, Iterable[A]]): ReadRecord.Service[F, (MatchCriteria, Json), Iterable[A]] = {

    val read = ReadRecord.lift[F, (MatchCriteria, Json), F[Iterable[A]]] {
      case (criteria, record) =>
        import cats.syntax.functor._
        val filter: A => Boolean = JsonFilterForCriteria.createFilterFor(criteria, record)

        data.list(QueryRange.All).map { all =>
          all.filter(filter)
        }
    }
    ReadRecord.flatMap(read)(identity)
  }

}
