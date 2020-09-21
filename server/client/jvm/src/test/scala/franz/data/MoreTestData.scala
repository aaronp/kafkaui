package franz.data

import cats.effect.IO
import franz.data.crud.ReadRecord
import franz.data.query.{MatchCriteria, ReadRecordDatabase}
import io.circe.Json

final case class MoreTestData(text: String = "", integer: Int = 0)

object MoreTestData {
  implicit val encoder = io.circe.generic.semiauto.deriveEncoder[MoreTestData]
  implicit val decoder = io.circe.generic.semiauto.deriveDecoder[MoreTestData]

  def instances: Seq[MoreTestData] = {
    for {
      name <- Seq("Susan", "Peter", "Carl")
      num <- Seq(1, 2, 3, 10, 100, 2000)
    } yield {
      MoreTestData(name, num)
    }
  }

  def database(data: Seq[MoreTestData] = instances): ReadRecord.Service[IO, (MatchCriteria, Json), Seq[MoreTestData]] = ReadRecordDatabase(data)
}
