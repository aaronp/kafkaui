package franz.data.query

import franz.data.{HasKey, IsVersioned, VersionedRecord}
import io.circe.Json

/**
 * A wrapper for some DB result
 *
 * @tparam A
 */
trait QueryResponse[A] { self =>
  def versioning: IsVersioned[A]

  def idForA: HasKey[A, String]

  def totalResults: Int

  def values: Iterable[A]

  def ++(other: QueryResponse[A]): QueryResponse[A] = {
    QueryResponse.Joined(self, other)
  }
}

object QueryResponse {

  case class Joined[A](lhs: QueryResponse[A], rhs: QueryResponse[A]) extends QueryResponse[A] {
    override def versioning: IsVersioned[A] = lhs.versioning

    override def idForA: HasKey[A, String] = lhs.idForA

    override def totalResults: Int = lhs.totalResults + rhs.totalResults

    override def values: Iterable[A] = lhs.values ++ rhs.values
  }

  case class Inst[A](override val values: Iterable[VersionedRecord[A]]) extends QueryResponse[VersionedRecord[A]] {
    override def versioning: IsVersioned[VersionedRecord[A]] = implicitly[IsVersioned[VersionedRecord[A]]]

    override def idForA: HasKey[VersionedRecord[A], String] = HasKey[VersionedRecord[A], String]

    override lazy val totalResults: Int = values.size
  }

  def apply[A](values: Iterable[VersionedRecord[Json]]): Inst[Json] = new Inst(values)

  def apply[A](value: VersionedRecord[Json], theRest: VersionedRecord[Json]*): Inst[Json] = {
    new Inst(value +: theRest)
  }

  def empty[A]: QueryResponse[A] = Empty[A]

  case class Empty[A]() extends QueryResponse[A] {
    override def versioning = IsVersioned.lift[A](_ => 0)

    override def idForA = HasKey.lift[A, String](_ => "Nothing")

    override val totalResults: Int = 0

    override val values: Iterable[Nothing] = Iterable.empty
  }

}
