package franz.users

import cats.{Applicative, Monad, ~>}
import franz.data.crud.InsertRecord
import franz.data.{VersionedJson, VersionedJsonResponse}
import franz.rest.Swagger
import franz.users.Login.{Request, Response, Service, liftF}
import io.circe.{Decoder, Encoder}
import monix.eval.Task

import scala.util.Properties

trait CreateUser[F[_]] {
  def createUserService: CreateUser.Service[F]

}

object CreateUser {

  trait Service[F[_]] extends CreateUser[F] {
    self =>
    override def createUserService: CreateUser.Service[F] = this

    def createUser(newUser: CreateUser.Request): F[CreateUser.Response]

    final def mapCreateUserK[G[_]](implicit ev : F ~> G) : Service[G] = {
      liftF[G] { in =>
        ev(createUser(in))
      }
    }
  }
  
  def lift[F[_] : Applicative](thunk: Request => Response): Service[F] = {
    liftF(x => Applicative[F].pure(thunk(x)))
  }

  def liftF[F[_]](thunk: Request => F[Response]): Service[F] = new Service[F] {
    override def createUser(newUser: Request) = thunk(newUser)
  }

  final case class Request(userName: String, email: String, password: String)

  object Request {
    implicit val encoder = io.circe.generic.semiauto.deriveEncoder[Request]
    implicit val decoder = io.circe.generic.semiauto.deriveDecoder[Request]
  }

  sealed trait Response {
    def success: Boolean
  }

  object Response {

    case class CreatedUser(user: User) extends Response {
      override val success = true
    }

    object CreatedUser {
      implicit val encoder = io.circe.generic.semiauto.deriveEncoder[CreatedUser]
      implicit val decoder = io.circe.generic.semiauto.deriveDecoder[CreatedUser]
    }

    case class InvalidRequest(error: String) extends Response {
      override val success = false
    }

    object InvalidRequest {
      implicit val encoder = io.circe.generic.semiauto.deriveEncoder[InvalidRequest]
      implicit val decoder = io.circe.generic.semiauto.deriveDecoder[InvalidRequest]
    }


    import cats.syntax.functor._
    import io.circe.syntax._

    implicit val encoder: Encoder[Response] = Encoder.instance {
      case msg@InvalidRequest(_) => msg.asJson
      case msg@CreatedUser(_) => msg.asJson
    }
    implicit val decoder: Decoder[Response] = Decoder[InvalidRequest].widen.or(Decoder[CreatedUser].widen)
  }

  final case class Client[F[_] : Monad, A](client: Swagger.Client[F, A], parser: Swagger.Parser[F, A, Response], swagger: UserSwagger = UserSwagger.Rest) extends Service[F] {
    import cats.syntax.flatMap._

    override def createUser(request: Request): F[Response] = {
      client.run(swagger.users.createRequest(request)).flatMap(parser.apply)
    }
  }
}
