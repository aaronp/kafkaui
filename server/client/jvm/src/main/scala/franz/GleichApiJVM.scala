package franz

import com.typesafe.config.Config
import franz.jwt.Hmac256

object GleichApiJVM {

  def seedForConfig(rootConfig: Config) = Hmac256.asSecret(rootConfig.getString("franz.jwt.seed"))

}
