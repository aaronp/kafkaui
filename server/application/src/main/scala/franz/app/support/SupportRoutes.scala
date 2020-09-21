package franz.app.support

import cats.{Applicative, Defer}
import franz.firestore.GCloud
import io.circe.syntax._
import org.http4s.dsl.Http4sDsl
import org.http4s.{HttpRoutes, Response}

import scala.concurrent.{Await, Future}
//import cats.implicits._
import org.http4s.circe._

import scala.concurrent.duration._

object SupportRoutes {


  def apply[F[_] : Applicative : Defer](dsl: Http4sDsl[F] = Http4sDsl[F]): HttpRoutes[F] = {
    import dsl._
    HttpRoutes.of[F] {
      case req@GET -> Root / "gcloud" => Ok(GCloud.debug().asJson)
      case req@GET -> Root / "env" =>
        val envMap: Map[String, String] = sys.env.toMap
        Ok(envMap.asJson)
      case req@GET -> Root / "desc" =>
        import scala.concurrent.ExecutionContext.Implicits._
        val futureResp: Future[F[Response[F]]] = GCloud.googleDesc.map { pears =>
          Ok(pears.toMap.asJson)
        }
        Await.result(futureResp, 10.seconds)
      //        Ok(futureResp)
    }
  }
}
