package franz.rest

import cats.data.OptionT
import cats.effect.IO
import franz.users.User
import io.circe.{Decoder, Json}
import org.http4s.headers.Authorization
import org.http4s._


case class WrappedResponse(response: Response[IO]) {
  override def toString = {
    s"${status} : $body w/ headers ${response.headers}"
  }
  def status: Status = response.status

  lazy val bodyOpt: Option[String] = {
    franz.rest.parseBodyIO(OptionT.pure(response)).unsafeRunSync()
  }

  def body: String = bodyOpt.getOrElse("")

  def jsonBody: Json = io.circe.parser.parse(bodyOpt.get).toTry.get

  def bodyAs[A: Decoder]: A = jsonBody.as[A].toTry.get

  def authHeader: Credentials = authOpt.get

  def authOpt: Option[Credentials] = {
    response.headers.get(Authorization).map(_.credentials)
  }

  def authToken: Option[String] = {
    authOpt.collect {
      case Credentials.Token(AuthScheme.Bearer, jwt) => jwt
    }
  }
}

object WrappedResponse {

  def apply(routeUnderTest: HttpRoutes[IO], request: Request[IO]): WrappedResponse = {
    new WrappedResponse(responseFor(request, routeUnderTest).unsafeRunSync())
  }

  def bodyFor(request: Request[IO], routeUnderTest: HttpRoutes[IO]): IO[Option[String]] = {
    val responseT: OptionT[IO, Response[IO]] = routeUnderTest.run(request)
    val bodyT: OptionT[IO, String] = responseT.semiflatMap { resp: Response[IO] =>
      EntityDecoder.decodeString(resp)
    }
    bodyT.value
  }

  def responseFor(request: Request[IO], routeUnderTest: HttpRoutes[IO]): IO[Response[IO]] = {
    routeUnderTest.run(request).value.map(_.get)
  }

  def bodyForAuthed(request: AuthedRequest[IO, User], routeUnderTest: AuthedRoutes[User, IO]): IO[Option[String]] = {
    val responseT: OptionT[IO, Response[IO]] = routeUnderTest.run(request)
    franz.rest.parseBodyIO(responseT)
  }
}
