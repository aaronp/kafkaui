package franz.data

/**
 * Prepresents a means to determine a key from a value
 *
 * @tparam A the value type
 * @tparam K the key type
 */
trait HasKey[-A, K] {
  self =>

  def keyFor(value: A): K

  final def map[B](f: K => B) = new HasKey[A, B] {
    override def keyFor(value: A): B = f(self.keyFor(value))
  }

  final def contraMap[B](f: B => A) = new HasKey[B, K] {
    override def keyFor(value: B) = self.keyFor(f(value))
  }
}

object HasKey {

  object syntax {

    implicit class HasKeySyntax[A](val value: A) extends AnyVal {
      def key[B](implicit ev: HasKey[A, B]) = HasKey[A, B].keyFor(value)
    }

  }

  def apply[A, B](implicit ev: HasKey[A, B]): HasKey[A, B] = ev

  implicit object ForVersionedRecord extends HasKey[VersionedRecord[Nothing], String] {
    override def keyFor(value: VersionedRecord[Nothing]): String = value.id
  }

  def versionedId[A]: HasKey[Versioned[A], A] = new HasKey[Versioned[A], A] {
    override def keyFor(value: Versioned[A]): A = value.data
  }

  def lift[A, K](thunk: A => K) = new HasKey[A, K] {
    override def keyFor(value: A): K = thunk(value)
  }
}
