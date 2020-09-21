package franz.client

import java.util.UUID

import cats.effect.IO
import franz.app.AppClient
import org.scalajs.dom.{Event, KeyboardEvent}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Success, Try}

package object js {

  import FutureImplicits._

  val appClientIO: AppClient[IO] = AppClient(Swagger4js.jsonClientIO)
  val appClientFuture: AppClient[Future] = AppClient(Swagger4js.jsonClient)

  def uniqueId() = UUID.randomUUID().toString.filter(_.isLetterOrDigit)

  def isEnter(e: KeyboardEvent): Boolean = {
    // keyCode can be undefined
    e != null && e.keyCode != null && e.keyCode == 13
  }

  def onEnter(thunk: => Unit) = (e: KeyboardEvent) => {
    if (isEnter(e)) {
      thunk
    }
  }

  def onEnterCancel(thunk: => Unit) = (e: KeyboardEvent) => {
    if (isEnter(e)) {
      e.cancel()
      thunk
    }
  }

  implicit class RichKeyboardEvent(val event: KeyboardEvent) extends AnyVal {
    def onEnter(thunk: => Unit) = {
      if (isEnter(event)) {
        thunk
      }
    }
  }

  implicit class RichEvent(val event: Event) extends AnyVal {
    def cancel() = {
      event.stopPropagation()
      event.preventDefault()
    }
  }

}
