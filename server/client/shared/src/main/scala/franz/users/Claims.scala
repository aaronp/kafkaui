package franz.users

import java.nio.charset.StandardCharsets
import java.time.{Instant, ZoneOffset, ZonedDateTime}
import java.util.Base64

import franz.users.Claims.asNumericDate
import io.circe
import io.circe.Decoder.Result
import io.circe._
import io.circe.parser._
import io.circe.syntax._
import cats.syntax.either._

import scala.concurrent.duration._

/**
 * https://en.m.wikipedia.org/wiki/JSON_Web_Token
 *
 * @param name    the user name
 * @param userId  the user ID
 * @param email   the user email
 * @param roleStr a comma-separated list of roles this user has
 * @param iss     Identifies principal that issued the JWT.
 * @param sub     Identifies the subject of the JWT.
 * @param aud     Identifies the recipients that the JWT is intended for. Each principal intended to process the JWT must identify itself with a value in the audience claim.
 * @param exp     Expires: Identifies the expiration time on and after which the JWT must not be accepted for processing
 * @param nbf     NotBefore: the time on which the JWT will start to be accepted for processing
 * @param iat     the time at which the JWT was issued
 * @param jti     Case sensitive unique identifier of the token even among different issuers.
 */
case class Claims(
                   name: String = null,
                   userId: String = null,
                   email: String = null,
                   roleStr: String = null,
                   iss: String = null,
                   sub: String = null,
                   aud: String = null,
                   exp: Claims.NumericDate = 0L,
                   nbf: Claims.NumericDate = 0L,
                   iat: Claims.NumericDate = 0L,
                   jti: String = null
                 ) {

  def sessionDuration: Option[FiniteDuration] = {
    for {
      issued <- issuedAt
      expires <- expiresAt
      jd = java.time.Duration.between(issued, expires)
      duration <- Either.catchOnly[ArithmeticException](jd.toNanos.nanos).toOption
    } yield duration
  }

  def expiresAt: Option[ZonedDateTime] = Option(exp).filterNot(_ <= 0).map(Claims.fromNumericDate)

  def issuedAt: Option[ZonedDateTime] = Option(iat).filterNot(_ <= 0).map(Claims.fromNumericDate)

  def withExpiry(expires: ZonedDateTime): User = {
    copy(exp = asNumericDate(expires))
  }

  def incExpiry(sessionDuration: FiniteDuration, now: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC)): User = {
    val newExpiry = now.plusNanos(sessionDuration.toNanos)
    withExpiry(newExpiry)
  }

  def withIssuedAt(time: ZonedDateTime): User = {
    copy(iat = asNumericDate(time))
  }

  def matchesUsernameOrEmail(usernameOrEmail: String): Boolean = {
    Option(name).exists(_.equalsIgnoreCase(usernameOrEmail)) ||
      Option(email).exists(_.equalsIgnoreCase(usernameOrEmail))
  }

  def setRoles(first: String, theRest: String*): Claims = {
    setRoles(theRest.toSet + first)
  }

  def withId(userId: String): Claims = copy(userId = userId)

  def withEmail(emailValue: String): Claims = copy(email = emailValue)

  def setRoles(roles: Set[String]): Claims = {
    roles.foreach(r => require(!r.contains(",")))
    copy(roleStr = roles.mkString(","))
  }


  private def asSet(str: String) = {
    Option(str).fold(Set.empty[String])(_.split(",", -1).toSet)
  }

  lazy val roles = asSet(roleStr)

  def isExpired(now: ZonedDateTime) = {
    exp != 0 && exp <= Claims.asNumericDate(now)
  }

  def toJson: String = Claims.toJson(this)

  def toJsonBase64: String = {
    val bytes = Base64.getUrlEncoder.encode(toJson.getBytes(StandardCharsets.UTF_8))
    new String(bytes, StandardCharsets.UTF_8)
  }
}

object Claims {

  type EpochMillis = Long

  type NumericDate = EpochMillis

  def asNumericDate(d8: ZonedDateTime): NumericDate = d8.toInstant.toEpochMilli

  def fromNumericDate(epochMillis: EpochMillis): ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC)

  implicit object ClaimsDecoder extends Decoder[Claims] {
    override def apply(c: HCursor): Result[Claims] = {
      def fld[A: Decoder](key: String, default: A) = c.downField(key).success.flatMap(_.as[A].toOption).getOrElse(default)

      Right(
        new Claims(
          name = fld[String]("name", null),
          userId = fld[String]("userId", null),
          email = fld[String]("email", null),
          roleStr = fld[String]("roles", null),
          iss = fld[String]("iss", null),
          sub = fld[String]("sub", null),
          aud = fld[String]("aud", null),
          exp = fld[Long]("exp", 0),
          nbf = fld[Long]("nbf", 0),
          iat = fld[Long]("iat", 0),
          jti = fld[String]("jti", null)
        ))
    }
  }

  implicit object ClaimsEncoder extends Encoder[Claims] {
    override def apply(c: Claims): Json = {
      import c._

      val stringMap = Map(
        "name" -> name,
        "userId" -> userId,
        "email" -> email,
        "roles" -> roleStr,
        "iss" -> iss,
        "sub" -> sub,
        "aud" -> aud,
        "jti" -> jti
      ).view.filter {
        case (_, null) => false
        case (_, "") => false
        case _ => true
      }
        .mapValues(Json.fromString)

      val numMap = Map(
        "nbf" -> nbf,
        "iat" -> iat,
        "exp" -> exp
      ).withFilter {
        case (_, 0L) => false
        case _ => true
      }
        .map {
          case (k, v) => (k, Json.fromLong(v))
        }

      val obj = JsonObject.fromMap((stringMap ++ numMap).toMap)
      Json.fromJsonObject(obj)
    }
  }

  def forUser(name: String, issued: ZonedDateTime = ZonedDateTime.now()): Claims = {
    new Claims(name = name, exp = 0L, iat = asNumericDate(issued))
  }

  def after(expiry: FiniteDuration, now: ZonedDateTime = ZonedDateTime.now()) = new {
    def forUser(name: String): Claims = {
      val expires = now.plusNanos(expiry.toNanos)
      new Claims(name = name, exp = asNumericDate(expires), iat = asNumericDate(now))
    }
  }

  def toJson(c: Claims): String = {
    c.asJson.noSpaces
  }

  def fromJson(j: String): Either[circe.Error, Claims] = {
    decode[Claims](j)
  }

}
