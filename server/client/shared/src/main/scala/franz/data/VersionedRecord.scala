package franz.data

import java.util.UUID

import io.circe.{Decoder, Encoder, Json}

import scala.util.Try

/**
 * A wrapper around some data as a means to add additional fields
 *
 * @param id                 the record id
 * @param userId             the user who created this record
 * @param data               the data we're versioning
 * @param version            the version (versions start at zero)
 * @param createdEpochMillis the time at which this record was created as a UTC epoch. Records are append-only, so 'created' makes sense.
 *                           We keep a 'latest' and 'version' history of records, so the 'created' could be interpreted as 'updated' for versions > 0
 * @tparam A
 */
final case class VersionedRecord[A](data: A, userId: String, id: String, version: Int, createdEpochMillis: Long) {
  require(version >= 0)

  def asCoords(collectionName: CollectionName) = RecordCoords(collectionName, id, version)

  def mapToJson(implicit aEncoder: Encoder[A]): VersionedRecord[Json] = map(aEncoder.apply)

  def map[B](f: A => B): VersionedRecord[B] = copy(data = f(data))

  def mapAs[B](implicit decoder: Decoder[B], ev: A =:= Json): Try[VersionedRecord[B]] = {
    ev(data).as[B].toTry.map { b =>
      map { _ =>
        b
      }
    }
  }

  def incVersion = withVersion(version + 1)

  def withVersion(v: Int) = copy(version = v)

  def withData[B](newValue: B): VersionedRecord[B] = copy(data = newValue)

  def withUser(newUserId: String): VersionedRecord[A] = copy(userId = newUserId)

  def isFirstVersion = version == 0

  override def toString: String =
    s"""VersionedRecord(id: $id @ $version by user '$userId' at $createdEpochMillis) {
       |$data
       |}""".stripMargin
}

object VersionedRecord {

  object syntax {

    implicit class VersionSyntax[A](val data: A) extends AnyVal {
      def versionedRecord(userId: String = "", id: String = UUID.randomUUID().toString, version: Int = 0, created: Long = nowEpoch): VersionedRecord[A] = {
        VersionedRecord(data, userId, id, version, created)
      }
    }

  }

  implicit def versionedId[A] = new HasKey[VersionedRecord[A], String] {
    override def keyFor(value: VersionedRecord[A]): String = value.id
  }


  /**
   * Mongo Queries need to refer to the fields of 'VersionedRecord' by name (strings), which we keep here
   * just to tie things together for readability and to keep them close to the actual fields themselves
   */
  object fields {
    val Id = "id"
    val Version = "version"
    val Data = "data"
  }

  def nowEpoch() = {
    //Instant.now(Clock.systemUTC()).toEpochMilli
    System.currentTimeMillis()
  }

  /**
   * @param data    the data to wrap
   * @param userId  the current user ID
   * @param id      the id of this record
   * @param version the version of the record, defaults to zero
   * @param created a created timestamp.
   * @tparam A the wrapped type
   * @return a VersionedRecord[A]
   */
  def apply[A](data: A, userId: String = "anon", id: String = UUID.randomUUID().toString, version: Int = 0, created: Long = nowEpoch): VersionedRecord[A] = {
    new VersionedRecord[A](data, userId, id, version, created)
  }

  implicit def encoder[A: Encoder] = io.circe.generic.semiauto.deriveEncoder[VersionedRecord[A]]

  implicit def decoder[A: Decoder] = io.circe.generic.semiauto.deriveDecoder[VersionedRecord[A]]
}
