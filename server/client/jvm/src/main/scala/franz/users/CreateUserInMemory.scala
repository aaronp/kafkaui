package franz.users

import java.util.UUID

import cats.effect.concurrent.Ref
import cats.effect.{IO, Sync}
import cats.syntax.all._
import cats.{Applicative, Monad}
import franz.UserApi
import franz.jwt.Hmac256
import franz.users.RichClaims._
import javax.crypto.spec.SecretKeySpec


final case class CreateUserInMemory[F[_] : Monad](users: Ref[F, Map[User, String]], seed : SecretKeySpec) extends CreateUser.Service[F] with Login.Service[F] {

  override def createUser(newUser: CreateUser.Request): F[CreateUser.Response] = {
    val opt: F[Option[CreateUser.Response]] = users.tryModify { users =>
      if (users.keySet.exists(_.matchesUsernameOrEmail(newUser.userName))) {
        users -> CreateUser.Response.InvalidRequest("User already exists")
      } else {
        val user = Claims.forUser(newUser.userName).withEmail(newUser.email).withId(UUID.randomUUID().toString)
        val newMap = users.updated(user, newUser.password)
        newMap -> CreateUser.Response.CreatedUser(user)
      }
    }
    opt.map {
      case None => CreateUser.Response.InvalidRequest(s"Unable to create '${newUser.userName}''")
      case Some(resp) => resp
    }
  }

  override def login(request: Login.Request): F[Login.Response] = {
    users.get.map { pwdUser: Map[User, String] =>

      val found = pwdUser.find {
        case (user, pwd) =>
          val ok = user.name == request.usernameOrEmail || user.email == request.usernameOrEmail
          ok && pwd == request.password
      }

      found.fold(Login.Response.empty()) {
        case (user, _) =>
          import RichClaims._
          Login.Response(user.asToken(seed), user)
      }
    }
  }

  override def logout(userToken : String): F[Boolean] = {
    users.modify { pwdUser: Map[User, String] =>
      val toRemove: Option[(User, String)] = pwdUser.find {
        case (user, _) =>
          user.asToken(seed) == userToken
      }
      toRemove.fold(pwdUser -> false) {
        case (u, _) =>
          val newMap = pwdUser - u
          newMap -> true
      }
    }
  }
}

object CreateUserInMemory {

  def empty[F[_] : Monad : Sync](cache: JWTCache[F], seed: SecretKeySpec): F[UserApi[F]] = {
    val fRef = Ref.of[F, Map[User, String]](Map.empty[User, String])
    fRef.map { ref =>
      apply(ref, cache, seed)
    }
  }

  def unsafe(cache: JWTCache[IO], seed: SecretKeySpec): UserApi[IO] = {
    val ref = Ref.unsafe[IO, Map[User, String]](scala.collection.immutable.Map.empty[User, String])
    apply(ref, cache, seed)
  }

  def apply[F[_] : Monad](tokens: Ref[F, Map[User, String]], cache: JWTCache[F], seed: SecretKeySpec): UserApi[F] = {
    val inMemory = new CreateUserInMemory[F](tokens, seed)
    val loginService = cache.jwtCache.wrap(inMemory)
    UserApi(loginService, inMemory)
  }

}
