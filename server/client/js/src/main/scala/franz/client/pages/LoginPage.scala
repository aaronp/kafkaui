package franz.client.pages

import franz.client.js.{HtmlUtils, MessageAlerts, appClientFuture, _}
import franz.users.{CreateUser, Login}
import org.scalajs.dom.raw.Event

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scala.util.{Failure, Success}

@JSExportTopLevel("LoginPage")
object LoginPage {


  def showMsg(msgDivId: String, text: String) = {
    HtmlUtils.divById(msgDivId).map(d => MessageAlerts(d)).foreach { messages =>
      messages.pushMessage(text)
    }
  }

  @JSExport
  def onCreateUser(emailDivId: String, pwdDivId: String, msgDivId: String, event: Event) = {
    val email = HtmlUtils.valueOf(emailDivId)
    val pwd = HtmlUtils.valueOf(pwdDivId)

    event.cancel()

    appClientFuture.userApi.createUserService.createUser(CreateUser.Request(email, email, pwd)).onComplete {
      case Success(CreateUser.Response.CreatedUser(user)) => showMsg(msgDivId, s"Created user: ${user.name}")
      case Success(CreateUser.Response.InvalidRequest(err)) => showMsg(msgDivId, s"Invalid Request: $err")
      case Failure(err) => showMsg(msgDivId, s"Computer says no: ${err.getMessage}")
    }
  }

  @JSExport
  def onLogin(emailDivId: String, pwdDivId: String, msgDivId: String, event: Event) = {
    val email = HtmlUtils.valueOf(emailDivId)
    val pwd = HtmlUtils.valueOf(pwdDivId)

    event.cancel()

    appClientFuture.userApi.loginService.login(Login.Request(email, pwd)).onComplete {
      case Success(Login.Response(Some(value))) =>
        HtmlUtils.log(s"Logged in $value")
        HtmlUtils.redirectTo("index.html")
      case Success(Login.Response(None)) =>
        showMsg(msgDivId, s"Nope. Try again")
      case Failure(err) =>
        showMsg(msgDivId, s"Computer says no: $err")
    }
  }
}
