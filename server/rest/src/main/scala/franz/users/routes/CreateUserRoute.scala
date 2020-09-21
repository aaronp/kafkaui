package franz.users.routes


import cats.Monad
import cats.effect.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._
import franz.users.{CreateUser, UserSwagger}
import io.circe.syntax._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.{HttpRoutes, Status}

object CreateUserRoute {

  def apply[F[_] : Sync](service: CreateUser.Service[F]): HttpRoutes[F] = {
    makeRoutes(service, Http4sDsl[F])
  }

  private def makeRoutes[F[_] : Sync : Monad](service: CreateUser.Service[F], dsl: Http4sDsl[F]): HttpRoutes[F] = {
    import dsl._

    implicit val decoder = jsonOf[F, CreateUser.Request]
    HttpRoutes.of[F] {
      case req@POST -> Root / UserSwagger.Users =>
        for {
          request <- req.as[CreateUser.Request]
          response: CreateUser.Response <- service.createUser(request)
          httpResp <- Ok(response.asJson)
        } yield {
          if (response.success) httpResp else httpResp.withStatus(Status.BadRequest)
        }
    }
  }
}
