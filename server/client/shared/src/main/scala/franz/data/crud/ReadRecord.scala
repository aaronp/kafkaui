package franz.data.crud

import cats.{Applicative, Functor, Monad, ~>}
import franz.data.crud.InsertRecord.{Service, contraMap}
import franz.users.User

/**
 *
 * @tparam F the effect type
 * @tparam Q the query (or key) type used to retrieve a record
 * @tparam A the result record type
 */
trait ReadRecord[F[_], -Q, A] {

  def readService: ReadRecord.Service[F, Q, A]

}

object ReadRecord {

  /**
   * The read service - return some value 'A' for query input 'Q'
   *
   * @tparam F the effect type
   * @tparam Q the query (or key) type used to retrieve a record
   * @tparam A the result record type
   */
  trait Service[F[_], -Q, A] extends ReadRecord[F, Q, A] {
    self =>
    override def readService: Service[F, Q, A] = self

    final def contractMapRead[B](f: B => Q): Service[F, B, A] = {
      contraMap(self)(f)
    }

    final def mapReadK[G[_]](implicit ev: F ~> G): Service[G, Q, A] = new Service[G, Q, A] {
      override def read(request: Q): G[A] = ev(self.read(request))
    }

    def read(query: Q): F[A]
  }

  final def ignoringUser[F[_], Q, A](self: Service[F, Q, A]): Service[F, (User, Q), A] = {
    contraMap[F, (User, Q), Q, A](self)(_._2)
  }

  final def contraMap[F[_], B, Q, A](self: Service[F, Q, A])(f: B => Q): Service[F, B, A] = {
    liftF(query => self.read(f(query)))
  }

  final def contraFlatMap[F[_], Q, A, B](self: Service[F, Q, A])(f: B => F[Q])(implicit monad: Monad[F]): Service[F, B, A] = {
    liftF(request => monad.flatMap(f(request))(self.read))
  }

  /** @return a service based on this one which aps the query result to a different type
   */
  final def map[F[_], Q, A, B](self: Service[F, Q, A])(f: A => B)(implicit functor: Functor[F]): Service[F, Q, B] = {
    liftF(request => functor.map(self.read(request))(f))
  }

  final def flatMap[F[_], Q, A, B](self: Service[F, Q, A])(f: A => F[B])(implicit monad: Monad[F]): Service[F, Q, B] = {
    liftF(request => monad.flatMap(self.read(request))(f))
  }

  def lift[F[_], Q, A](read: Q => A)(implicit ap: Applicative[F]): Service[F, Q, A] = {
    liftF[F, Q, A] { query =>
      ap.pure(read(query))
    }
  }

  def liftF[F[_], Q, A](thunk: Q => F[A]): Service[F, Q, A] = {
    new Service[F, Q, A] {
      override def read(query: Q): F[A] = thunk(query)
    }
  }
}
