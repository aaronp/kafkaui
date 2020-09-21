package franz.client.js

import cats.effect.{ContextShift, IO}
import franz.client.state.AppState
import franz.errors.{InvalidRequest, MissingPermissions}
import franz.rest.{Settings, Swagger}
import franz.users.CreateUser
import io.circe.parser.parse
import io.circe.{Decoder, Json}
import org.scalajs.dom.XMLHttpRequest
import org.scalajs.dom.ext.{Ajax, AjaxException}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Try}

object Swagger4js {

  object implicits {

    /**
     * Exposes means to work with json bodies
     *
     * @param resp
     */
    implicit class RichSwaggerResponse(val resp: XMLHttpRequest) extends AnyVal {
      def bodyAs[A: Decoder]: Try[A] = parse(resp.responseText).toTry.flatMap(_.as[A].toTry)

      def bodyAsFuture[A: Decoder]: Future[A] = {
        val tri = bodyAs[A].recoverWith {
          case exp =>
            println(s"response ${resp} status '${resp.status}' w/ body >${resp.responseText}< responseURL=>${resp.responseURL}< threw $exp")
            val message = parseError(exp)
            Failure(new Exception(message))
        }
        Future.fromTry(tri)
      }


      def parseError(original: Throwable): String = {
        niceError.getOrElse(s"Error: ${original.getMessage}")
      }

      def niceError: Try[String] = {
        bodyAsInvalidRequest.map { err =>
          s"Invalid Request: ${err.invalidRequest}"
        }.orElse {
          bodyAsMissingPermissions.map { err =>
            s"Unauthorized - user is missing permission '${err.requiredPermission}'"
          }
        }.orElse {
          bodyAsCreateError.map { err =>
            s"Create failed: '${err}'"
          }
        }
      }

      def bodyAsInvalidRequest: Try[InvalidRequest] = bodyAs[InvalidRequest]

      // TODO - fix this
      private def bodyAsCreateError: Try[CreateUser.Response.InvalidRequest] = bodyAs[CreateUser.Response.InvalidRequest]

      def bodyAsMissingPermissions: Try[MissingPermissions] = bodyAs[MissingPermissions]
    }

    private implicit val fakeJSShift = new ContextShift[IO] {
      override def shift: IO[Unit] = IO.unit

      override def evalOn[A](ec: ExecutionContext)(fa: IO[A]): IO[A] = fa
    }

    /**
     * Offers an '.ajax' post-fix operation to swagger requests to execute them as ajax requests
     *
     * @param r
     */
    implicit class RichSwaggerRequest(val r: Swagger.Request) extends AnyVal {
      def ajax: Future[XMLHttpRequest] = Swagger4js(r)
      def ajaxIO: IO[XMLHttpRequest] = IO.fromFuture(IO.delay(ajax))
    }

  }

  import implicits._

  lazy val client: Swagger.Client[Future, XMLHttpRequest] = Swagger.Client(_.ajax)

  lazy val clientIO: Swagger.Client[IO, XMLHttpRequest] = Swagger.Client(_.ajaxIO)

  lazy val jsonClient: Swagger.Client[Future, Json] = {
    import FutureImplicits._
    client.flatMapF(_.bodyAsFuture[Json])
  }
  lazy val jsonClientIO = {
    clientIO.flatMapF { response =>
      IO.fromTry(response.bodyAs[Json])
    }
  }

  def apply(req: Swagger.Request, jwtToken: Option[String] = AppState.get().currentToken()): Future[XMLHttpRequest] = {
    val future: Future[XMLHttpRequest] = send(req, jwtToken)

    val recovered = future.recoverWith {
      case err: AjaxException =>
        HtmlUtils.log(s"Send failed with ${err.xhr.responseText}")
        import implicits._
        val msg = err.xhr.parseError(err)
        Future.failed(new Exception(msg))
    }

    recovered.map(updateJWT)
  }

  def send(req: Swagger.Request, jwtToken: Option[String]): Future[XMLHttpRequest] = {
    val headers = jwtToken.fold(Map.empty[String, String]) { jwt =>
      Map(
        "Authorization" -> s"Bearer $jwt",
        Settings.AccessHeader -> jwt
      )
    }

    val future = req match {
      case Swagger.GetRequest(url) => Ajax.get(url, headers = headers)
      case Swagger.PostRequest(url, Some(body)) => Ajax.post(url, data = body.noSpaces, headers = headers)
      case Swagger.PostRequest(url, None) => Ajax.post(url, headers = headers)
      case Swagger.DeleteRequest(initialUrl, queryParams) =>
        val fullQuery = {
          if (queryParams.isEmpty) {
            initialUrl
          } else {
            queryParams.map { case (k, v) => s"$k=$v" }.mkString(s"$initialUrl?", ",", "")
          }
        }
        Ajax.delete(fullQuery, headers = headers)
    }
    future
  }

  private val BearerR = "Bearer (.*)".r

  private def updateJWT(resp: XMLHttpRequest): XMLHttpRequest = {
    resp.getResponseHeader("Authorization") match {
      case BearerR(jwt) =>
        AppState.updateToken(jwt)
      case _ =>
        Option(resp.getResponseHeader(Settings.AccessHeader)) match {
          case Some(text) if text.trim.nonEmpty =>
            val jwt = text.trim
            AppState.updateToken(jwt)
          case _ =>
        }
    }
    resp
  }
}
