package franz.client.pages

import cats.data.Validated.{Invalid, Valid}
import franz.client.bootstrap.Component
import franz.client.js.HtmlUtils
import franz.data.{CollectionName, RecordCoords, RecordVersion}
import io.circe.Json
import io.circe.syntax._

sealed trait NavLocation {
  def state: Json

  def asPath: List[String]

  def size: Int
}

object NavLocation {
  def forCollection(name: CollectionName) = RecordLocation(name)

  def apply(coors: RecordCoords, theRest: String*): RecordLocation = {
    RecordLocation(coors) match {
      case location if theRest.isEmpty => location
      case location => location.copy(asPath = location.asPath ::: theRest.toList)
    }
  }

  case object Home extends NavLocation {
    override val state = "home".asJson

    override def size: Int = 0

    override def asPath: List[String] = List("Home")
  }

  case class RecordLocation private(override val asPath: List[String]) extends NavLocation {
    override val state = RecordLocation.encoder(this)

    def collection: CollectionName = asPath.head

    def idOpt: Option[String] = asPath.tail.headOption

    def coordsOpt = asPath match {
      case c :: id :: v :: _ =>
        RecordVersion.parse(v) match {
          case Invalid(oops) =>
            // TODO
            HtmlUtils.raiseError(s"NavLocation.bad version: $oops")
            None
          case Valid(version) =>
            Option(RecordCoords(c, id, version))
        }
      case _ => None
    }

    override def size: Int = asPath.size
  }

  object RecordLocation {
    def apply(collection: CollectionName) = new RecordLocation(collection :: Nil)

    def apply(coords: RecordCoords) = {
      new RecordLocation(
        coords.collection ::
          coords.id ::
          coords.version.queryValue ::
          Nil)
    }

    implicit val encoder = io.circe.generic.semiauto.deriveEncoder[RecordLocation]
    implicit val decoder = io.circe.generic.semiauto.deriveDecoder[RecordLocation]
  }

}

