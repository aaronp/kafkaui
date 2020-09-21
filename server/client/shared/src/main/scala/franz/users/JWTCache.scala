package franz.users

import cats.effect.concurrent.Ref
import cats.effect.{IO, Sync}
import cats.syntax.apply._
import cats.syntax.functor._
import cats.{Functor, Monad, ~>}

/**
 * Represents a user lookup for the given JWT token
 *
 * @tparam F
 */
trait JWTCache[F[_]] {
  def jwtCache: JWTCache.Service[F]
}

object JWTCache {

  trait Service[F[_]] extends JWTCache[F] {
    self =>

    /**
     * Wraps a login service, setting the JWT cache on successful login
     *
     * @param service the login service
     * @param M
     * @return a new login service which sets the token on login
     */
    def wrap(service: Login.Service[F])(implicit M: Monad[F]): Login.Service[F] = {
      def doLogin(request: Login.Request) = {
        M.flatMap(service.login(request)) { response =>
          response.tokenAndUser match {
            case Some((jwt, user)) => set(jwt, user) *> M.pure(response)
            case None => M.pure(response)
          }
        }
      }

      def doLogout(jwt: String) = {
        M.flatMap(service.logout(jwt)) { response =>
          logout(jwt) *> M.pure(response)
        }
      }

      Login.liftF[F](doLogin, doLogout)
    }

    def mapK[G[_]](implicit ev: F ~> G): Service[G] = new Service[G] {
      override def lookup(jwt: String): G[Option[(JWT, User)]] = ev(self.lookup(jwt))
      override def set(jwt: JWT, user: User): G[Unit] = ev(self.set(jwt, user))
      override def logout(jwt: JWT): G[Unit] = ev(self.logout(jwt))
    }

    /**
     * Find a user by a json web token
     *
     * @param jwt the key
     * @return the found user with an optionally updated token
     */
    def lookup(jwt: String): F[Option[(JWT, User)]]

    def set(jwt: JWT, user: User): F[Unit]

    def logout(jwt: JWT): F[Unit]

    override def jwtCache: JWTCache.Service[F] = self
  }

  sealed trait Request {
    type Response
  }

  object Request {

    final case class Lookup(jwt: String) extends Request {
      override type Response = Option[(JWT, User)]
    }

    final case class Set(jwt: String, user: User) extends Request {
      override type Response = Unit
    }

    final case class Logout(jwt: String) extends Request {
      override type Response = Unit
    }

  }

  def unsafe[F[_] : Sync]: JWTCache[F] = {
    val ref = Ref.unsafe[F, Map[JWT, User]](scala.collection.immutable.Map.empty[JWT, User])
    apply(ref)
  }

  def empty[F[_] : Sync]: F[JWTCache[F]] = {
    val fRef: F[Ref[F, Map[JWT, User]]] = Ref.of[F, Map[JWT, User]](Map.empty[JWT, User])
    fRef.map { ref =>
      apply(ref)
    }
  }

  def apply[F[_] : Functor](tokens: Ref[F, Map[JWT, User]]): JWTCache[F] = new InMemory[F](tokens)

  def forIO(): IO[JWTCache[IO]] = {
    import cats.effect.IO._
    Ref.of[IO, Map[JWT, User]](scala.collection.immutable.Map.empty[JWT, User]).map { mapRef =>
      new InMemory[IO](mapRef)
    }
  }

  class InMemory[F[_] : Functor](tokens: Ref[F, Map[JWT, User]]) extends Service[F] {

    def lookup(jwt: JWT) = tokens.get.map(_.get(jwt).map(u => (jwt, u)))

    def set(jwt: JWT, user: User): F[Unit] = tokens.update(_.updated(jwt, user))

    def logout(jwt: JWT): F[Unit] = tokens.update(_.removed(jwt))
  }

}
