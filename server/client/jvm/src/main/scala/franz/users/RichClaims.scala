package franz.users

import java.time.{ZoneOffset, ZonedDateTime}

import cats.{Applicative, Monad}
import franz.jwt.JsonWebToken

import javax.crypto.spec.SecretKeySpec

import scala.concurrent.duration.FiniteDuration

class RichClaims(val claims: Claims) extends AnyVal {

  def asToken(secret: SecretKeySpec): String = JsonWebToken.asHmac256Token(claims, secret)

  def asJsonWebToken(secret: SecretKeySpec): JsonWebToken = {
    JsonWebToken.parseToken(asToken(secret)).getOrElse(sys.error("bug: jwt <-> Claims is brokenNM"))
  }

  /**
   * Refresh (invalidate the old token, set the new) on the given cache service
   *
   * @param originalJWT
   * @param cache
   * @param sessionDuration
   * @param seed
   * @tparam F
   * @return
   */
  def refresh[F[_] : Monad](originalJWT: JWT,
                            cache: JWTCache.Service[F],
                            userRoles: UserRoles.Service[F],
                            sessionDuration: FiniteDuration,
                            seed: SecretKeySpec,
                            now: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC)): F[WebUser] = {
    val originalUser = claims

    import cats.syntax.flatMap._
    userRoles.rolesForUser(originalUser.userId).flatMap { roles =>
      val newUser = originalUser.incExpiry(sessionDuration, now).setRoles(roles)
      val newJWT: String = RichClaims.asRichClaims(newUser).asToken(seed)

      // invalidate the old token
      val logoutF: F[Unit] = Monad[F].unit // cache.logout(originalJWT)

      // set the new token
      val refreshF: F[Unit] = cache.set(newJWT, newUser)

      val cacheOpsAsToken = Applicative[F].point[(Unit, Unit) => (String, User)] {
        case _ => (newJWT, newUser)
      }
      Applicative[F].ap2(cacheOpsAsToken)(logoutF, refreshF)
    }
  }
}

object RichClaims {
  implicit def asRichClaims(claims: Claims) = new RichClaims(claims)

  def forJWT(jwt: String, secret: SecretKeySpec): Either[JsonWebToken.JwtError, User] = {
    JsonWebToken.forToken(jwt, secret).map(_.claims)
  }
}
