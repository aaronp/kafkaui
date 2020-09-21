package franz.data.crud

import cats.{Applicative, Functor, Monad, ~>}
import franz.data.QueryRange

trait ListRecords[F[_], A] {

  def listService: ListRecords.Service[F, A]

}

object ListRecords {

  trait Service[F[_], A] extends ListRecords[F, A] {
    self =>
    override def listService: ListRecords.Service[F, A] = self

    def list(range: QueryRange): F[A]

    final def list(from: Int, limit: Int = 100): F[A] = list(QueryRange(from, limit))

    final def mapListK[G[_]](implicit ev: F ~> G): Service[G, A] = new Service[G, A] {
      override def list(range: QueryRange): G[A] = ev(self.list(range))
    }

    final def mapListResults[B](f : A => B)(implicit F : Functor[F]): Service[F, B] = new Service[F, B] {
      override def list(range: QueryRange) = F.map(self.list(range))(f)
    }
  }

  final def map[F[_], A, B](self: Service[F, A])(f: A => B)(implicit functor: Functor[F]): Service[F, B] = {
    liftF(range => functor.map(self.list(range))(f))
  }

  final def flatMap[F[_], A, B](self: Service[F, A])(f: A => F[B])(implicit monad: Monad[F]): Service[F, B] = {
    liftF(range => monad.flatMap(self.list(range))(f))
  }

  def lift[F[_] : Applicative, A](thunk: QueryRange => A): Service[F, A] = {
    liftF(thunk.andThen(Applicative[F].point))
  }

  def liftF[F[_], A](thunk: QueryRange => F[A]): Service[F, A] = new Service[F, A] {
    override def list(range: QueryRange) = thunk(range)
  }
}
