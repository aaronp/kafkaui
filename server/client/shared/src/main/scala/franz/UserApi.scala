package franz

import cats.{ApplicativeError, Monad, ~>}
import franz.rest.Swagger
import franz.users._
import io.circe.Json

/** @tparam F the effect type
 */
trait UserApi[F[_]] extends Login[F] with CreateUser[F] {
  def mapK[G[_]](implicit ev : F ~> G) : UserApi[G] = {
    UserApi(loginService.mapLoginK[G], createUserService.mapCreateUserK[G])
  }
}


object UserApi {
  def apply[F[_]](loginInstance: Login.Service[F],
                   createUserInstance: CreateUser.Service[F]): UserApi[F] = {
    new UserApi[F] {
      override val loginService = loginInstance

      override val createUserService = createUserInstance
    }
  }


  /**
   * Return a [[UserApi]] instance using the given json client
   *
   * @param client      the client which knows how to send/receive JSON requests
   * @param userSwagger the user request definitions
   * @param appErr      a means of lifting errors into F[A]
   * @tparam F the effect type
   * @return a GleichApi instance
   */
  def client[F[_] : Monad](client: Swagger.Client[F, Json],
                           userSwagger: UserSwagger = UserSwagger.Rest
                          )(implicit appErr: ApplicativeError[F, Throwable]): UserApi[F] = {
    val loginParser = Swagger.parserForJson[F, Login.Response]
    val logoutParser = Swagger.parserForJson[F, Boolean]
    val createParser = Swagger.parserForJson[F, CreateUser.Response]
    UserApi[F](
      Login.Client(client, loginParser, logoutParser),
      CreateUser.Client(client, createParser)
    )
  }

}
