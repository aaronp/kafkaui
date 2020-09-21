package franz.data.crud

import io.circe.{Decoder, Encoder}

import scala.util.Try


/**
 * So, er, yah der 'ey! I sees yas gots a Response, A!
 *
 * @tparam A
 */
sealed trait InsertResponse[+A] {
  self =>
  def isSuccess: Boolean = toEither.isRight

  def failed: Boolean = !isSuccess

  def toEither: Either[Throwable, A]

  def toTry: Try[A] = toEither.toTry

  def map[B](f : A => B) : InsertResponse[B]
}

/**
 * @param suppliedVersion the version attempted
 * @param currentVersion the current version, if accessible/known
 * @tparam A
 */
case class InvalidDetailedResponse[+A](suppliedVersion: Int, currentVersion : Option[Int] = None, detail : Option[String] = None) extends Exception(s"Invalid Version: supplied version was $suppliedVersion${currentVersion.fold("")(cv => s" current version is $cv")} ${detail.getOrElse("")}") with InsertResponse[A] {
  require(!currentVersion.exists(_ == suppliedVersion - 1), s"currentVersion '$currentVersion' shouldn't be the supplied version ('${suppliedVersion}') - 1")
  override def toEither = Left(this)
  override def map[B](f : A => B) : InsertResponse[B] = this.asInstanceOf[InsertResponse[B]]
}

object InvalidDetailedResponse {
  implicit def encoder[A: Encoder] = io.circe.generic.semiauto.deriveEncoder[InvalidDetailedResponse[A]]

  implicit def decoder[A: Decoder] = io.circe.generic.semiauto.deriveDecoder[InvalidDetailedResponse[A]]
}

final case class InsertSuccess[+A](newVersion: Int, newValue: A) extends InsertResponse[A] {
  override def toEither = Right(newValue)
  override def map[B](f : A => B) = copy(newValue = f(newValue))
}

object InsertSuccess {
  implicit def encoder[A: Encoder] = io.circe.generic.semiauto.deriveEncoder[InsertSuccess[A]]

  implicit def decoder[A: Decoder] = io.circe.generic.semiauto.deriveDecoder[InsertSuccess[A]]
}

object InsertResponse {
  def invalidVersion[A](suppliedVersion: Int): InsertResponse[A] = {
    InvalidDetailedResponse(suppliedVersion)
  }

  def inserted[A](newVersion: Int, newValue: A): InsertResponse[A] = InsertSuccess(newVersion, newValue)

  import cats.syntax.functor._

  implicit def encoder[A: Encoder]: Encoder[InsertResponse[A]] = Encoder.instance[InsertResponse[A]] {
    case msg@InvalidDetailedResponse(_, _, _) => InvalidDetailedResponse.encoder[A].apply(msg)
    case msg@InsertSuccess(_, _) => InsertSuccess.encoder[A].apply(msg)
  }

  implicit def decoder[A: Decoder]: Decoder[InsertResponse[A]] = {
    val insertedResp: Decoder[InsertResponse[A]] = Decoder[InsertSuccess[A]].widen

    insertedResp
      .or(Decoder[InvalidDetailedResponse[A]].widen)
  }
}
