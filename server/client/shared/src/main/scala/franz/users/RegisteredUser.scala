package franz.users

import java.time.{LocalDateTime, ZoneOffset, ZonedDateTime}

case class RegisteredUser(userName: String, email: String, password: String, created: LocalDateTime) {
  def asClaims: User = {
    Claims.forUser(userName).withId(userName).withEmail(email)
  }
}

object RegisteredUser {

  def apply(request: CreateUser.Request, now: LocalDateTime = ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime): RegisteredUser = {
    new RegisteredUser(request.userName, request.email, request.password, now)
  }

  implicit val encoder = io.circe.generic.semiauto.deriveEncoder[RegisteredUser]
  implicit val decoder = io.circe.generic.semiauto.deriveDecoder[RegisteredUser]
}
