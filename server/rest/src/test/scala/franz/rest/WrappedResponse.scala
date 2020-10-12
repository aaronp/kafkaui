package franz.rest

import org.http4s.Response
import zio.Task

import scala.util.Try


final case class WrappedResponse[A](httpResponse : Response[Task], parsed : Try[A])
