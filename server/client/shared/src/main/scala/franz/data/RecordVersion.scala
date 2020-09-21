package franz.data

import io.circe.{Decoder, Encoder, Json}


sealed trait RecordVersion {
  /**
   *
   * @return a query value representing this version (e.g. 5^ is the previous version to 5, 8 is the version 8, 'latest' is the latest version)
   **/
  def queryValue: String
}

case class PreviousVersion(previousToVersion: Version) extends RecordVersion {
  override def queryValue: String = s"$previousToVersion-"
}

object PreviousVersion {
  implicit val encoder = io.circe.generic.semiauto.deriveEncoder[PreviousVersion]
  implicit val decoder = io.circe.generic.semiauto.deriveDecoder[PreviousVersion]
}

case class NextVersion(afterVersion: Version) extends RecordVersion {
  override def queryValue: String = s"$afterVersion+"
}

object NextVersion {
  implicit val encoder = io.circe.generic.semiauto.deriveEncoder[NextVersion]
  implicit val decoder = io.circe.generic.semiauto.deriveDecoder[NextVersion]
}

case object LatestVersion extends RecordVersion {
  override def queryValue: String = "latest"

  implicit val encoder = Encoder.instance[LatestVersion.type](_ => Json.fromString(queryValue))
  implicit val decoder: Decoder[LatestVersion.type] = Decoder.decodeString.emap {
    case value if value == LatestVersion.queryValue => Right(LatestVersion)
    case other => Left(s"expected '${queryValue}' but got '$other'")
  }
}

case class ExplicitVersion(version: Version) extends RecordVersion {
  override def queryValue: String = version.toString
}

object ExplicitVersion {
  implicit val encoder = io.circe.generic.semiauto.deriveEncoder[ExplicitVersion]
  implicit val decoder = io.circe.generic.semiauto.deriveDecoder[ExplicitVersion]
}

object RecordVersion {

  import io.circe.syntax._

  def previous(v: Int): RecordVersion = PreviousVersion(v)
  def next(v: Int): RecordVersion = NextVersion(v)

  def apply(v: Int): RecordVersion = ExplicitVersion(v)

  def latest: RecordVersion = LatestVersion

  private val PreviousVersionR = "(\\d+)-".r
  private val NextVersionR = "(\\d+)[\\+ ]$".r
  private val VersionR = "(\\d+)$".r

  import cats.data._
  import cats.implicits._

  def parse(version: String): Validated[String, RecordVersion] = {
    version match {
      case PreviousVersionR(v) => PreviousVersion(v.toInt).valid
      case NextVersionR(v) => NextVersion(v.toInt).valid
      case VersionR(v) => ExplicitVersion(v.toInt).valid
      case other if other == LatestVersion.queryValue => LatestVersion.valid
      case other => s"Expected '${LatestVersion.queryValue}', a specific integer, or an integer suffixed with a '-' or '+' for previous/next version, but got: '$other'".invalid
    }
  }

  def parseFromQueryParams(versionKey: String, queryParams: Map[String, Seq[String]]): Validated[String, RecordVersion] = {
    queryParams.getOrElse(versionKey, Nil) match {
      case Seq(versionStr) => parse(versionStr)
      case Seq() => LatestVersion.valid
      case many => s"${many.size} params found for ${versionKey}".invalid
    }
  }

  implicit val encoder: Encoder[RecordVersion] = Encoder.instance[RecordVersion] {
    case msg@PreviousVersion(_) => msg.asJson
    case msg@ExplicitVersion(_) => msg.asJson
    case msg@NextVersion(_) => msg.asJson
    case LatestVersion => LatestVersion.asJson
  }

  implicit val decoder: Decoder[RecordVersion] = {
    PreviousVersion.decoder.widen[RecordVersion]
      .or(NextVersion.decoder.widen[RecordVersion])
      .or(LatestVersion.decoder.widen[RecordVersion])
      .or(ExplicitVersion.decoder.widen[RecordVersion])
  }
}
