package franz.data.crud

import cats.{Applicative, Functor, Monad, ~>}
import franz.users.User

/**
 * A service for deleting data
 *
 * @tparam F
 * @tparam A
 */
trait DeleteRecord[F[_], A, R] {

  def deleteService: DeleteRecord.Service[F, A, R]
}

object DeleteRecord {

  trait Service[F[_], A, R] extends DeleteRecord[F, A, R] {
    self =>
    override def deleteService: DeleteRecord.Service[F, A, R] = self

    final def contractMapDelete[B](f: B => A): Service[F, B, R] = {
      contraMap(self)(f)
    }

    final def mapDeleteK[G[_]](implicit ev: F ~> G): Service[G, A, R] = new Service[G, A, R] {
      override def delete(record: A): G[R] = ev(self.delete(record))
    }

    final def mapDeleteResult[B](f: R => B)(implicit F: Functor[F]): Service[F, A, B] = new Service[F, A, B] {
      override def delete(record: A): F[B] = F.map(self.delete(record))(f)
    }
    final def flatMapDeleteResult[B](f: R => F[B])(implicit F: Monad[F]): Service[F, A, B] = new Service[F, A, B] {
      override def delete(record: A): F[B] = F.flatMap(self.delete(record))(f)
    }

    def delete(record: A): F[R]
  }

  final def ignoringUser[F[_], A, R](self: Service[F, A, R]): Service[F, (User, A), R] = {
    contraMap[F, A, (User, A), R](self)(_._2)
  }

  final def contraMap[F[_], A, B, R](self: Service[F, A, R])(f: B => A): Service[F, B, R] = {
    liftF { record => self.delete(f(record)) }
  }

  final def map[F[_] : Functor, A, R, R2](self: Service[F, A, R])(f: R => R2): Service[F, A, R2] = {
    liftF { record => Functor[F].map(self.delete(record))(f) }
  }

  final def contraFlatMap[F[_], A, B, R](self: Service[F, A, R])(f: B => F[A])(implicit monad: Monad[F]): Service[F, B, R] = {
    liftF { request => monad.flatMap(f(request))(self.delete) }
  }

  def lift[F[_] : Applicative, A, R](thunk: A => R): Service[F, A, R] = {
    liftF { id =>
      Applicative[F].pure(thunk(id))
    }
  }

  def liftF[F[_], A, R](thunk: A => F[R]): Service[F, A, R] = new Service[F, A, R] {
    override def delete(id: A) = thunk(id)
  }

}
