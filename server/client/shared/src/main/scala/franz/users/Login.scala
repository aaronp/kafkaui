package franz.users

import cats.{Applicative, Monad, ~>}
import franz.UserApi
import franz.rest.Swagger

/**
 * A trait which can be mixed-in with other services.
 *
 * @tparam F the effect type
 */
trait Login[F[_]] {
  def loginService: Login.Service[F]
}

object Login {

  implicit def apply[F[_]](implicit ev: Login[F]): Login[F] = ev

  def lift[F[_] : Applicative](thunk: Login.Request => Login.Response): Service[F] = {
    liftF(x => Applicative[F].pure(thunk(x)), _ => Applicative[F].point(false))
  }

  def liftF[F[_]](action: Login.Request => F[Login.Response], logoutAction : String => F[Boolean]): Service[F] = {
    new Service[F] {
      override def login(request: Login.Request): F[Login.Response] = action(request)
      override def logout(userToken : String): F[Boolean] = logoutAction(userToken)
    }
  }

  type JWT = String

  final case class Request(usernameOrEmail: String, password: String)

  object Request {
    implicit val encoder = io.circe.generic.semiauto.deriveEncoder[Request]
    implicit val decoder = io.circe.generic.semiauto.deriveDecoder[Request]
  }

  final case class Response(tokenAndUser: Option[(String, User)]) {
    def jwtToken: Option[String] = tokenAndUser.map(_._1)

    def user: Option[User] = tokenAndUser.map(_._2)
  }

  object Response {
    def empty() = new Response(None)

    def apply(token: String, user: User) = new Response(Option(token -> user))

    implicit val encoder = io.circe.generic.semiauto.deriveEncoder[Response]
    implicit val decoder = io.circe.generic.semiauto.deriveDecoder[Response]
  }

  /**
   * Log a user in
   *
   * @tparam F the effect type
   */
  trait Service[F[_]] extends Login[F] {
    self =>
    def login(request: Login.Request): F[Login.Response]

    def logout(userToken : String): F[Boolean]

    /**
     * Replace the logout function for this service
     * @param newLogout
     * @return the new service
     */
    final def withLogout(newLogout : String => F[Boolean]) = {
      liftF(login, newLogout)
    }

    override def loginService: Login.Service[F] = self

    final def mapLoginK[G[_]](implicit ev : F ~> G) : Service[G] = {
      liftF[G](in => ev(login(in)), token => ev(logout(token)))
    }
  }

  final case class Client[F[_] : Monad, A](client: Swagger.Client[F, A],
                                           loginParser: Swagger.Parser[F, A, Response],
                                           logoutParser: Swagger.Parser[F, A, Boolean],
                                           swagger: UserSwagger = UserSwagger.Rest) extends Service[F] {

    import cats.syntax.flatMap._

    override def login(request: Request): F[Response] = client.run(swagger.login(request)).flatMap(loginParser.apply)

    override def logout(userToken : String): F[Boolean] = client.run(swagger.logout(userToken)).flatMap(logoutParser.apply)
  }

}




