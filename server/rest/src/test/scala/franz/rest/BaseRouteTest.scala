package franz.rest

import io.circe.Decoder
import org.http4s._
import zio.Task
import zio.interop.catz._

import scala.util.Try

abstract class BaseRouteTest extends BaseTest {

  implicit class RichRoute(route: HttpRoutes[Task]) {
    def responseFor(request: Request[Task]): Response[Task] = responseForOpt(request).getOrElse(sys.error("no response"))

    def responseForOpt(request: Request[Task]): Option[Response[Task]] = {
      route(request).value.value()
    }
  }

  implicit class RichResponse(response: Response[Task]) {

    def bodyAsString: String = bodyTask.value()

    def bodyAs[A: Decoder]: Try[A] = io.circe.parser.decode[A](bodyAsString).toTry

    def bodyTask: Task[String] = EntityDecoder.decodeText(response)
  }

  def get(url: String, queryParams: (String, String)*): Request[Task] = {
    val uri: Uri = asUri(url, queryParams: _*)
    Request[Task](method = Method.GET, uri = uri)
  }

  def post(url: String, body: String, queryParams: (String, String)*): Request[Task] = {
    val uri: Uri = asUri(url, queryParams: _*)
    Request[Task](method = Method.POST, uri = uri).withEntity(body)
  }

  private def asUri(url: String, queryParams: (String, String)*) = {
    val encoded = Uri.encode(url)
    val uri = if (queryParams.isEmpty) {
      Uri.unsafeFromString(encoded)
    } else {
      Uri.unsafeFromString(encoded).withQueryParams(queryParams.toMap)
    }
    uri
  }

}
