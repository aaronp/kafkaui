package franz.data

/**
 * Represents a type from which we can determine a version
 *
 * @tparam A the value type
 */
trait IsVersioned[-A] {
  /** @param value the input value
   * @return the version
   */
  def versionFor(value: A): Int

  def isFirstVersion(value: A) = versionFor(value) == 0
}

object IsVersioned {

  def lift[A](thunk: A => Int) = new IsVersioned[A] {
    override def versionFor(value: A): Int = thunk(value)
  }

  implicit object identity extends IsVersioned[Int] {
    override def versionFor(value: Int): Int = value
  }

  implicit def asVersionedRecordIsVersioned[A]: IsVersioned[VersionedRecord[A]] = new IsVersioned[VersionedRecord[A]] {
    override def versionFor(value: VersionedRecord[A]): Int = value.version
  }

  implicit def asVersionedIsVersioned[A]: IsVersioned[Versioned[A]] = new IsVersioned[Versioned[A]] {
    override def versionFor(value: Versioned[A]): Int = value.version
  }
}
