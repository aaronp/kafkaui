package franz.users

import cats.{ApplicativeError, Monad}
import franz.rest.Swagger
import io.circe.Json

trait UserHealth[F[_]] {

  def userStatus: F[User]
}

object UserHealth {
  def liftF[F[_]](user: => F[User]): UserHealth[F] = new UserHealth[F] {
    override def userStatus: F[User] = user
  }

  final case class Client[F[_] : Monad, A](client: Swagger.Client[F, A], parser: Swagger.Parser[F, A, User], swagger: UserSwagger) extends UserHealth[F] {

    import cats.syntax.flatMap._

    override def userStatus: F[User] = client.run(swagger.users.status).flatMap(parser.apply)
  }

  object Client {
    def apply[F[_] : Monad](client: Swagger.Client[F, Json], swagger: UserSwagger = UserSwagger.Rest)(implicit appErr: ApplicativeError[F, Throwable]): Client[F, Json] = {
      new Client[F, Json](client, Swagger.parserForJson[F, User], swagger)
    }
  }

}
