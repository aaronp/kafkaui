package franz.client.state

import franz.client.pages.NavLocation
import franz.users.Login
import org.scalajs.dom

/**
 * Some local state
 *
 * @param jwtOpt
 */
case class AppState(jwtOpt: Option[String], currentPath: NavLocation) {
  def withLocation(location: NavLocation) = {
    dom.window.sessionStorage.setItem("locationState", location.state.noSpaces)
    copy(currentPath = location)
  }

  def updateIfLonger(location: NavLocation): AppState = {
    if (location.size > currentPath.size) {
      withLocation(location)
    } else {
      this
    }
  }

  def currentToken(): Option[String] = {
    val token = dom.window.sessionStorage.getItem("jwtToken")
    Option(token).filterNot(_.isEmpty).orElse(jwtOpt)
  }

  def withResponse(loginResponse: Login.Response): AppState = {
    withToken(loginResponse.jwtToken)
  }

  def withToken(jwt: Option[String]): AppState = {
    jwt.foreach { token: String =>
      dom.window.sessionStorage.setItem("jwtToken", token)
    }
    copy(jwtOpt = jwt.orElse(jwtOpt))
  }

  def withToken(token: String): AppState = {
    dom.window.sessionStorage.setItem("jwtToken", token)
    copy(jwtOpt = Option(token))
  }
}

object AppState {

  def get() = state

  def onLogin(response: Login.Response): Login.Response = {
    state = state.withResponse(response)
    response
  }

  def updateToken(jwt: String): AppState = {
    state = state.withToken(jwt)
    state
  }

  def updateToken(jwtOpt: Option[String]): AppState = {
    state = state.withToken(jwtOpt)
    state
  }

  private var state = AppState(None, NavLocation.Home)
}
