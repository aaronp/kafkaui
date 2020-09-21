package franz.db.impl

object TestData {

  case class Nested(foo: Long)

  object Nested {
    implicit val encoder = io.circe.generic.semiauto.deriveEncoder[Nested]
    implicit val decoder = io.circe.generic.semiauto.deriveDecoder[Nested]
  }

  case class SomeRecord(counter: Int = 42, name: String = "example", flag : Boolean = false, child: Nested = Nested(123), array: List[Nested] = Nil)

  object SomeRecord {
    implicit val encoder = io.circe.generic.semiauto.deriveEncoder[SomeRecord]
    implicit val decoder = io.circe.generic.semiauto.deriveDecoder[SomeRecord]
  }

  case class DifferentRecord(number: Int = 71, comment: String = "different", flag :Boolean = false)

  object DifferentRecord {
    implicit val encoder = io.circe.generic.semiauto.deriveEncoder[DifferentRecord]
    implicit val decoder = io.circe.generic.semiauto.deriveDecoder[DifferentRecord]
  }
}
