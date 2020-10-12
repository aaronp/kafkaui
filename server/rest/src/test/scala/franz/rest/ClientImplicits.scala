package franz.rest

import franz.rest.kafka.routes.TopicDesc
import io.circe.Decoder
import org.http4s.{EntityDecoder, Response}
import zio.interop.catz._
import zio.{CancelableFuture, Task}

import scala.util.Try

trait ClientImplicits {

  implicit def asRichZIO[A](zio: => Task[A])(implicit rt: _root_.zio.Runtime[_root_.zio.ZEnv]) = new {
    def value(): A = rt.unsafeRun(zio)

    def valueFuture(): CancelableFuture[A] = rt.unsafeRunToFuture(zio)
  }

  implicit def forResponse(response: Response[Task])(implicit rt: zio.Runtime[zio.ZEnv]) = new {

    def bodyAs[A: Decoder]: Try[A] = io.circe.parser.decode[A](bodyAsString).toTry

    def wrapped[A: Decoder]: WrappedResponse[A] = WrappedResponse(response, bodyAs[A])

    def bodyTask: Task[String] = EntityDecoder.decodeText(response)

    def bodyAsString: String = bodyTask.value()
  }

}

object ClientImplicits extends ClientImplicits