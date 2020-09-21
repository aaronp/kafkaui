package franz.data

import cats.syntax.option._

case class RecordCoords(collection: CollectionName, id: Id, version: RecordVersion = LatestVersion) {
  def previous = withVersion(previousVersion)

  def next: RecordCoords = withVersion(nextVersion)

  def withVersion(v: RecordVersion) = copy(version = v)

  private def previousVersion = versionNumOpt.fold[RecordVersion](LatestVersion)(PreviousVersion.apply)

  private def nextVersion = versionNumOpt.fold[RecordVersion](NextVersion(0))(NextVersion.apply)

  def versionNumOpt: Option[Version] = version match {
    case PreviousVersion(n) => n.some
    case NextVersion(n) => n.some
    case ExplicitVersion(n) => n.some
    case LatestVersion => none
  }
}

object RecordCoords {
  def apply(collection: CollectionName, id: Id, version: Int) = {
    new RecordCoords(collection, id, ExplicitVersion(version))
  }

  def latest(collection: CollectionName, id: Id) = {
    new RecordCoords(collection, id, LatestVersion)
  }

  def previous(collection: CollectionName, id: Id, version: Int) = {
    new RecordCoords(collection, id, PreviousVersion(version))
  }

  def next(collection: CollectionName, id: Id, version: Int) = {
    new RecordCoords(collection, id, NextVersion(version))
  }

  implicit val encoder = io.circe.generic.semiauto.deriveEncoder[RecordCoords]
  implicit val decoder = io.circe.generic.semiauto.deriveDecoder[RecordCoords]
}
