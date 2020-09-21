package franz.data

import cats.Functor
import io.circe.{Decoder, Encoder}

final case class Versioned[A](version : Int, data : A) {
  def inc(newData : A = data) = copy(version + 1, data = newData)
}

object Versioned {

  implicit object VersionedFunctor extends Functor[Versioned] {
    override def map[A, B](fa: Versioned[A])(f: A => B): Versioned[B] = Versioned[B](fa.version, f(fa.data))
  }
  def apply[A](data : A) = new Versioned(0, data)

  object syntax {
    implicit class VersionSyntax[A](val data: A) extends AnyVal {
      def versioned(version: Int = 0): Versioned[A] = Versioned(version, data)
    }
    implicit class RichVersioned[A](val data: Versioned[A]) extends AnyVal {
      def withVersion(version: Int = 0): Versioned[A] = Versioned(version, data.data)
    }
  }

  implicit def encoder[A : Encoder]  = io.circe.generic.semiauto.deriveEncoder[Versioned[A]]
  implicit def decoder[A : Decoder] = io.circe.generic.semiauto.deriveDecoder[Versioned[A]]
}
