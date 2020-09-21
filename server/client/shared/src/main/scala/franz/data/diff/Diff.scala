package franz.data.diff

import cats.effect.Sync
import cats.{Applicative, NonEmptyParallel}
import donovan.json.ValuesByPath
import franz.data.crud.ReadRecord
import franz.data.{RecordCoords, VersionedJson, VersionedRecord}
import io.circe.{Encoder, Json}

trait Diff[F[_]] {

  def diffService: Diff.Service[F]
}

object Diff {

  trait Service[F[_]] {
    def diff(request: Request): F[Option[Result]]

    final def diff(leftHandSide: RecordCoords, rightHandSide: RecordCoords): F[Option[Result]] = diff(Request(leftHandSide, rightHandSide))
  }

  def apply[F[_] : Sync : NonEmptyParallel](reader: ReadRecord.Service[F, RecordCoords, Option[VersionedJson]]): Diff.Service[F] = {
    Diff.liftF[F] { request: Diff.Request =>
      import cats.syntax.parallel._
      (reader.read(request.leftHandSide), reader.read(request.rightHandSide)).parMapN {
        case (Some(a), Some(b)) => Option(Result(a, b))
        case (None, Some(b)) => Option(Result.right(b))
        case (Some(a), None) => Option(Result.left(a))
        case (None, None) => None
      }
    }
  }

  def lift[F[_] : Applicative](thunk: Request => Option[Result]): Service[F] = {
    liftF[F](thunk.andThen(Applicative[F].point))
  }

  def liftF[F[_]](thunk: Request => F[Option[Result]]): Service[F] = new Service[F] {
    override def diff(request: Request): F[Option[Result]] = thunk(request)
  }

  case class Request(leftHandSide: RecordCoords, rightHandSide: RecordCoords)

  object Request {
    implicit val encoder = io.circe.generic.semiauto.deriveEncoder[Request]
    implicit val decoder = io.circe.generic.semiauto.deriveDecoder[Request]
  }

  case class Result(diffs: Seq[RecordDiff]) {
    def formatted: Seq[String] = diffs.map(_.formatted)

    lazy val byPath: Map[String, RecordDiff] = {
      diffs.groupBy(_.path.mkString(".")).view.mapValues(_.ensuring(_.size == 1).head).toMap
    }

    def sorted = copy(diffs = diffs.sortBy(_.path.mkString(".")))

    override def toString: String = formatted.mkString(s"${diffs.size} diffs:\n\t", "\n\t", "\n")

    def leftValues: Map[Seq[String], Json] = {
      val values = diffs.collect {
        case RecordDiff(path, Some(left), _) => (path, left)
      }
      values.toMap.ensuring(_.size == values.size)
    }

    def rightValues: Map[Seq[String], Json] = {
      val values = diffs.collect {
        case RecordDiff(path, _, Some(right)) => (path, right)
      }
      values.toMap.ensuring(_.size == values.size)
    }
  }

  object Result {
    def right(value: VersionedJson): Result = {
      val diffs = donovan.json.ValuesByPath(value).map {
        case (path, value) => RecordDiff(path, None, Option(value))
      }
      Result(diffs.toSeq)
    }

    def left(value: VersionedJson): Result = {
      val diffs = ValuesByPath(value).map {
        case (path, value) => RecordDiff(path, Option(value), None)
      }
      Result(diffs.toSeq)
    }

    def of[A: Encoder](v1: VersionedRecord[A], v2: VersionedRecord[A]): Result = {
      import io.circe.syntax._
      apply(v1.withData(v1.data.asJson), v2.withData(v2.data.asJson))
    }

    def apply(v1: VersionedJson, v2: VersionedJson): Result = Result(RecordDiff(v1, v2))

    implicit val encoder = io.circe.generic.semiauto.deriveEncoder[Result]
    implicit val decoder = io.circe.generic.semiauto.deriveDecoder[Result]
  }

}
