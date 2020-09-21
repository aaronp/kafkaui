package franz.data.diff

import donovan.json.ValuesByPath
import franz.data.JsonAtPath.asString
import io.circe.{Encoder, Json}


case class RecordDiff(path: Seq[String], lhs: Option[Json], rhs: Option[Json]) {
  override def toString = formatted

  def lhsStr = lhs.map(asString).getOrElse("<not set>")

  def rhsStr = rhs.map(asString).getOrElse("<not set>")

  def formatted = path.mkString("", ".", s" => [$lhsStr, $rhsStr]")

}

object RecordDiff {

  def apply[A: Encoder, B: Encoder](leftInput: A, rightInput: B): Seq[RecordDiff] = {
    val lhs = ValuesByPath(leftInput)
    val rhs = ValuesByPath(rightInput)

    (lhs.keySet ++ rhs.keySet).foldLeft(Seq[RecordDiff]()) {
      case (diffs, path) =>
        (lhs.get(path), rhs.get(path)) match {
          case (Some(a), Some(b)) if a == b => diffs
          case (left, right) => RecordDiff(path, left, right) +: diffs
        }
    }
  }

  implicit val encoder = io.circe.generic.semiauto.deriveEncoder[RecordDiff]
  implicit val decoder = io.circe.generic.semiauto.deriveDecoder[RecordDiff]
}
