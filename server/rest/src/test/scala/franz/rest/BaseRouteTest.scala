package franz.rest

import org.http4s._
import zio.Task

abstract class BaseRouteTest extends BaseTest {

  implicit class RichRoute(route: HttpRoutes[Task]) {
    def responseFor(request: Request[Task]): Response[Task] = responseForOpt(request).getOrElse(sys.error("no response"))

    def responseForOpt(request: Request[Task]): Option[Response[Task]] = {
      route(request).value.value()
    }
  }


  def get(url: String, queryParams: (String, String)*): Request[Task] = RouteClient.get(url, queryParams:_*)

  def post(url: String, body: String, queryParams: (String, String)*): Request[Task] = RouteClient.post(url, body, queryParams:_*)

}
