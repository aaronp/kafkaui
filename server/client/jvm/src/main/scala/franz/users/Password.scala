package franz.users

import com.typesafe.config.Config
import franz.jwt.Hmac256
import javax.crypto.spec.SecretKeySpec

case class Password(secret: SecretKeySpec) {
  def hash(pwd: String) = Hmac256.encodeToString(secret, pwd)
}

object Password {
  def apply(secret: String): Password = {
    val seed = Hmac256.asSecret(secret)
    new Password(seed)
  }

  def apply(rootConfig: Config): Password = apply("pswrd" + rootConfig.getString("franz.pwdseed"))
}
