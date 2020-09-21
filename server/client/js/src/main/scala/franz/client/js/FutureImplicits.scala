package franz.client.js

import cats.{ApplicativeError, Monad}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scala.language.implicitConversions

object FutureImplicits extends FutureImplicits {

  case class FutureOps[A](future: Future[A]) extends AnyVal {
    def onSuccess(f: A => Unit)(implicit ec: ExecutionContext) = {
      future.onComplete {
        case Success(input) => f(input)
        case Failure(err) => HtmlUtils.log(s"future.onSuccess returned error: ${err.getMessage}")
      }
    }
  }
}

trait FutureImplicits {

  implicit def asFutureOps[A](future: Future[A]) = FutureImplicits.FutureOps[A](future)

  implicit def futureErr(implicit ec: ExecutionContext) = new ApplicativeError[Future, Throwable] {
    override def raiseError[A](e: Throwable): Future[A] = Future.failed(e)

    override def handleErrorWith[A](fa: Future[A])(f: Throwable => Future[A]): Future[A] = {
      fa.recoverWith {
        case err => f(err)
      }
    }

    override def pure[A](x: A): Future[A] = Future.successful(x)

    override def ap[A, B](ff: Future[A => B])(fa: Future[A]): Future[B] = {
      for {
        fnc <- ff
        a <- fa
      } yield {
        fnc(a)
      }
    }
  }

  implicit def futureMonad(implicit ec: ExecutionContext): Monad[Future] = new Monad[Future] {
    override def pure[A](x: A): Future[A] = Future.successful(x)

    override def flatMap[A, B](fa: Future[A])(f: A => Future[B]): Future[B] = {
      fa.flatMap(f)
    }

    override def tailRecM[A, B](a: A)(f: A => Future[Either[A, B]]): Future[B] = {
      val future: Future[Either[A, B]] = f(a)
      future.flatMap {
        case Left(_) => tailRecM[A, B](a)(f)
        case Right(b) => pure(b)
      }
    }
  }
}
