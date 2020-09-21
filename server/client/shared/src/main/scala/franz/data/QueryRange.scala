package franz.data


final case class QueryRange(from: Int, limit: Int) {
  require(limit >= 0)
  require(from >= 0)

  def fromIterable[A](seq: Iterable[A]): Iterable[A] = seq.drop(from).take(limit)
  def fromIterator[A](seq: Iterator[A]): Iterator[A] = seq.drop(from).take(limit)
}

object QueryRange {
  def All = QueryRange(0, Int.MaxValue)

  val Default = QueryRange(0, 1000)

  def fromQueryParams(queryParams: Map[String, Seq[String]]): Either[String, QueryRange] = {
    def intFromSeq(name: String, values: Seq[String]): Either[String, Int] = {
      import cats.syntax.either._
      values match {
        case Seq(only) => Either.catchNonFatal(only.toInt).leftMap(_ => s"'$name' wasn't an int: '${only}'")
        case Seq() => Left(s"'$name' not set")
        case many => Left(s"${many.size} values specified for '$name'")
      }
    }

    (queryParams.get("from"), queryParams.get("limit")) match {
      case (Some(froms), Some(limits)) =>
        (intFromSeq("from", froms), intFromSeq("limit", limits)) match {
          case (Right(f), Right(l)) => Right(QueryRange(f, l))
          case (Left(err), Right(_)) => Left(err)
          case (Right(_), Left(err)) => Left(err)
          case (Left(err1), Left(err2)) => Left(s"$err1 $err2")
        }
      case (Some(froms), None) => intFromSeq("from", froms).map(x => QueryRange(x, Default.limit))
      case (None, Some(limit)) => intFromSeq("limit", limit).map(x => QueryRange(Default.from, x))
      case (None, None) => Right(Default)
    }
  }

  implicit val encoder = io.circe.generic.semiauto.deriveEncoder[QueryRange]
  implicit val decoder = io.circe.generic.semiauto.deriveDecoder[QueryRange]
}
