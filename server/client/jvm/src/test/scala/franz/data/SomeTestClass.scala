package franz.data

import cats.effect.IO
import franz.data.crud.ReadRecord
import franz.data.query.{MatchCriteria, ReadRecordDatabase}
import io.circe.Json

final case class SomeTestClass(name: String = "anon", number: Int = 1234, truthy: Boolean = true)

object SomeTestClass {
  implicit val encoder = io.circe.generic.semiauto.deriveEncoder[SomeTestClass]
  implicit val decoder = io.circe.generic.semiauto.deriveDecoder[SomeTestClass]

  def instances: Seq[SomeTestClass] = {
    for {
      name <- Seq("Clark", "Peter", "Tony")
      num <- Seq(1, 2, 3, 10, 100, 2000)
      truthy <- Seq(true, false)
    } yield {
      SomeTestClass(name, num, truthy)
    }
  }

  def database(data: Seq[SomeTestClass] = instances): ReadRecord.Service[IO, (MatchCriteria, Json), Seq[SomeTestClass]] = ReadRecordDatabase(data)
}
