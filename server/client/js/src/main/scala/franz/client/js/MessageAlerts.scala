package franz.client.js

import org.scalajs.dom.html.Div
import scalatags.JsDom.all.{`class`, div, _}

import scala.concurrent.duration._
import scala.scalajs.js.timers.setTimeout

object MessageAlerts {
  val DefaultDuration = 2.seconds
  def info(visibleForDuration: FiniteDuration = DefaultDuration) = {
    new MessageAlerts(div(`class` := CSS.messages.MessageIn).render, visibleForDuration)
  }
  def error(visibleForDuration: FiniteDuration = 5.seconds) = {
    new MessageAlerts(div(`class` := CSS.messages.MessageError).render, visibleForDuration)
  }
}

/**
 * A little stateful class which can append alerts messages.
 *
 * This will allow you to push messages which will appear in the provided messageDiv
 */
case class MessageAlerts(messageDiv: Div, visibleForDuration: FiniteDuration = MessageAlerts.DefaultDuration) {

  private var infoMessages = List[(Div, Long)]()

  private def showNextInfo(): Unit = {
    infoMessages match {
      case Nil =>
        messageDiv.className = CSS.messages.MessageOut
        messageDiv.innerHTML = ""
      case _ =>
        val displayThreshold             = System.currentTimeMillis() - visibleForDuration.toMillis
        val (oldMessages, stillRelevant) = infoMessages.partition(_._2 < displayThreshold)
        infoMessages = stillRelevant
        oldMessages.foreach {
          case (div, _) => messageDiv.removeChild(div)
        }
        setTimeout(500.millis) {
          showNextInfo()
        }
    }

  }
  def pushMessage(text: String): Unit = {
    val elem = div(text).render
    messageDiv.appendChild(elem)
    infoMessages = (elem, System.currentTimeMillis()) :: infoMessages
    showNextInfo()
  }
}
