package franz.client.js

import java.util.UUID

import org.scalajs.dom
import org.scalajs.dom.html.Div
import org.scalajs.dom.raw._
import org.scalajs.dom.{document, html, window}

import scala.collection.immutable
import scala.util.control.NonFatal

object HtmlUtils extends HtmlUtils

trait HtmlUtils {

  /**
   * TODO - display this in an app footer
   *
   * @param strIn
   */
  def raiseError(strIn: String): Nothing = {
    val str = Option(strIn).getOrElse("raiseError called with null")
    showAlert(str)
    sys.error(str)
  }

  def fmt(date: Long) = {
    val d8 = new scala.scalajs.js.Date(date.toDouble)
    d8.toISOString()
  }

  def replace(id: String, node: Node): Node = {
    elmById(id).foreach { elm =>
      elm.innerHTML = ""
      elm.appendChild(node)
    }
    node
  }

  def divById(id: String): Option[Div] = elmById(id).collect {
    case div: Div => div
  }

  def elmById(id: String) = {
    Option(document.getElementById(id)) match {
      case opt @ None =>
        log(s"WARN: elmById couldn't find $id")
        opt
      case opt => opt
    }
  }

  def $(id: String) = elmById(id).getOrElse {
    showAlert(s"BUG trying to get an element: $id is null" )
    null
  }

  def childrenFor(html: HTMLElement): immutable.IndexedSeq[Node] = {
    (0 until html.childNodes.length).map { i =>
      html.childNodes.item(i)
    }
  }

  /**
   * Thanks SO!
   * https://stackoverflow.com/questions/503093/how-do-i-redirect-to-another-webpage
   *
   * @param page the page the URL to go to
   */
  def redirectTo(page: String) = {
    log(
      s"""
         |window.location.hostname=${window.location.hostname}
         |window.location.host=${window.location.host}
         |window.location.pathname=${window.location.pathname}
         |window.location.search=${window.location.search}
         |window.location.href=${window.location.href}
       """.stripMargin)

    window.location.replace(page)
  }

  /**
   * Thanks SO!
   * https://stackoverflow.com/questions/503093/how-do-i-redirect-to-another-webpage
   *
   * @param page the page the URL to go to
   */
  def gotoLink(page: String) = {
    window.location.href = page
  }

  def showAlert(text: String): Unit = {
    dom.window.alert(text)
  }

  val debugOn = true

  def debug(text: String): Unit = {
    if (debugOn) {
      log(text)
    }
  }

  def log(text: String): Unit = {
    dom.window.console.log(text)
  }

  def valueOfNonEmpty(id: String, uniqueId: => String = UUID.randomUUID.toString): String = {
    document.getElementById(id) match {
      case x: HTMLTextAreaElement =>
        Option(x.value).map(_.trim).filterNot(_.isEmpty).getOrElse {
          val default = uniqueId
          x.value = default
          default
        }
      case x: HTMLInputElement =>
        Option(x.value).map(_.trim).filterNot(_.isEmpty).getOrElse {
          val default = uniqueId
          x.value = default
          default
        }
      case other =>
        sys.error(s"valueOf('$id') was ${other}")
        uniqueId
    }
  }

  def valueOf(id: String, elm: Element = null): String = {
    try {
      val result = Option(elm).getOrElse(document.getElementById(id)) match {
        case x: HTMLTextAreaElement => x.value
        case x: HTMLInputElement => x.value
        case other =>
          sys.error(s"valueOf('$id') was ${other}")
      }
      result
    } catch {
      case NonFatal(err) =>
        dom.window.console.log(s"Couldn't get value for '$id': $err")
        ""
    }
  }

  def appendPar(targetNode: dom.Node, text: String): Unit = {
    val parNode: Element = dom.document.createElement("p")
    val textNode = document.createTextNode(text)
    parNode.appendChild(textNode)
    targetNode.appendChild(parNode)
  }

  def mouseMove(pre: html.Pre) = {
    pre.onmousemove = { (e: dom.MouseEvent) =>
      pre.textContent =
        s"""e.clientX ${e.clientX}
           |e.clientY ${e.clientY}
           |e.pageX   ${e.pageX}
           |e.pageY   ${e.pageY}
           |e.screenX ${e.screenX}
           |e.screenY ${e.screenY}
         """.stripMargin
    }
  }
}
