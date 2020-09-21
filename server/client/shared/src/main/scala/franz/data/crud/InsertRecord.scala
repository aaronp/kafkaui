package franz.data.crud

import cats.{Applicative, Functor, Monad, ~>}
import franz.users.User

/**
 * A service which can write things down
 *
 * @tparam F the effect type
 */
trait InsertRecord[F[_], A, R] {

  /** @return the service
   */
  def insertService: InsertRecord.Service[F, A, R]
}


/**
 * This pattern looks a bit like [[http://degoes.net/articles/zio-environment this]].
 *
 * I like how you just get a 'Request', 'Response' and 'Service' all in one place.
 *
 */
object InsertRecord {

  /**
   * The service definition
   *
   * @tparam F
   */
  trait Service[F[_], A, R] extends InsertRecord[F, A, R] {
    self =>
    override def insertService: InsertRecord.Service[F, A, R] = self

    /**
     * Insert a record
     *
     * @param request
     * @return the response
     */
    def insert(request: A): F[R]

    final def contractMapInsert[B](f: B => A): Service[F, B, R] = contraMap(self)(f)

    final def mapInsertResponse[B](f: R => B)(implicit F: Functor[F]): Service[F, A, B] = InsertRecord.map(self)(f)

    final def flatMapInsertResponse[B](f: R => F[B])(implicit M: Monad[F]): Service[F, A, B] = InsertRecord.flatMap(self)(f)

    final def mapInsertK[G[_]](implicit ev: F ~> G): Service[G, A, R] = new Service[G, A, R] {
      override def insert(request: A): G[R] = {
        ev(self.insert(request))
      }
    }
  }

  final def contraFlatMap[F[_], A, R, B](self: Service[F, A, R])(f: B => F[A])(implicit monad: Monad[F]): Service[F, B, R] = {
    liftF(request => monad.flatMap(f(request))(self.insert))
  }

  final def map[F[_], A, R, B](self: Service[F, A, R])(f: R => B)(implicit functor: Functor[F]): Service[F, A, B] = {
    liftF(request => functor.map(self.insert(request))(f))
  }

  final def flatMap[F[_], A, R, B](self: Service[F, A, R])(f: R => F[B])(implicit M: Monad[F]): Service[F, A, B] = {
    liftF(request => M.flatMap(self.insert(request))(f))
  }

  final def ignoringUser[F[_], A, R, B](self: Service[F, A, R]): Service[F, (User, A), R] = {
    contraMap[F, A, (User, A), R](self)(_._2)
  }

  def contraMap[F[_], A, B, R](self: Service[F, A, R])(f: B => A): Service[F, B, R] = {
    liftF { request: B =>
      self.insert(f(request))
    }
  }

  implicit def apply[F[_], A, R](implicit ev: InsertRecord[F, A, R]): InsertRecord[F, A, R] = ev

  def lift[F[_] : Applicative, A, R](thunk: A => R): Service[F, A, R] = {
    liftF(x => Applicative[F].pure(thunk(x)))
  }

  def liftF[F[_], A, R](thunk: A => F[R]): Service[F, A, R] = new Service[F, A, R] {
    override def insert(request: A): F[R] = thunk(request)
  }
}
