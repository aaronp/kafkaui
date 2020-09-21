package franz.client.pages

import franz.client.js.{HtmlUtils, appClientFuture}
import franz.client.state.AppState
import org.scalajs.dom.raw.Event
import scalatags.JsDom.all.{div, _}

import franz.client.js._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scala.util.{Failure, Success}

@JSExportTopLevel("IndexPage")
object IndexPage {

  @JSExport
  def onLoad(userDivId: String, navContainerId: String, mainContainerId: String): Unit = {
    val userSpan = HtmlUtils.$(userDivId)

    val nav = Nav(navContainerId, mainContainerId)
    nav.move.home()

    appClientFuture.healthClient.userStatus.onComplete {
      case Failure(err) =>
        HtmlUtils.log(s"IndexPage.onLoad does't like the appClientFuture.healthClient.userStatus result: $err")
        HtmlUtils.redirectTo("login.html")
      case Success(user) =>
        userSpan.innerHTML = ""
        userSpan.innerHTML = user.name
    }
  }

  @JSExport
  def onUserClick(userDiv: String): Unit = {
    val userSpan = HtmlUtils.$(userDiv)
  }

  @JSExport
  def onAccountClick(sidenavId: String, mainDivId: String): Unit = {
    HtmlUtils.showAlert("TODO")
  }

  @JSExport
  def onLogout(): Unit = {
    val jwt = AppState.get().currentToken().getOrElse("")
    franz.client.js.appClientFuture.userApi.loginService.logout(jwt).onComplete {
      case _ =>
        HtmlUtils.redirectTo("login.html")
    }
  }

  @JSExport
  def status(divId: String, event: Event) = {

    event.cancel()

    appClientFuture.healthClient.userStatus.onComplete {
      case Failure(err) =>
        val statusDiv = div(
          div(s"JWT:${AppState.get().currentToken()}"),
          div(s"ERR:$err")
        )

        HtmlUtils.replace(divId, statusDiv.render)
      case Success(user) =>
        val statusDiv = div(
          div(s"User:${user.name}"),
          div(s"Expires:${HtmlUtils.fmt(user.exp)}"),
          div(s"Issued:${HtmlUtils.fmt(user.iat)}"),
          div(s"Roles:${user.roleStr}")
        )

        HtmlUtils.replace(divId, statusDiv.render)
    }
  }
}
