package franz.rest


import java.time.{LocalDateTime, ZoneOffset, ZonedDateTime}

package object time {

  type Timestamp = ZonedDateTime

  implicit val ordering: Ordering[Timestamp] = Ordering.by[Timestamp, Long](_.toEpochSecond)

  private type Now = Timestamp

  type DateTimeResolver = Now => Timestamp

  def now(zone: ZoneOffset = ZoneOffset.UTC): ZonedDateTime = ZonedDateTime.now(zone)

  def fromEpochNanos(epochNanos: Long, zone: ZoneOffset = ZoneOffset.UTC): Timestamp = {
    val second = epochNanos / 1000000
    val nanos  = (epochNanos % 1000000).toInt
    LocalDateTime.ofEpochSecond(second, nanos, zone).atZone(zone)
  }
}
