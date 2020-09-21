package franz.jwt

import java.nio.charset.StandardCharsets
import java.util.Base64

import com.typesafe.config.Config
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

final class PasswordHash private[jwt](salt: Array[Byte], iterationCount: Int, keyLen: Int) {

  private val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
  private val enc = Base64.getEncoder

  def apply(password: String): String = {
    val spec = new PBEKeySpec(password.toCharArray, salt, iterationCount, keyLen)
    enc.encodeToString(skf.generateSecret(spec).getEncoded)
  }
}

object PasswordHash {

  def apply(rootConfig: Config): PasswordHash = {
    val config = rootConfig.getConfig("franz.pwd")
    val salt = config.getString("salt") match {
      case "" => "P3ppr"
      case value => value
    }
    apply(
      salt = salt.getBytes(StandardCharsets.UTF_8),
      iterationCount = config.getInt("iterationCount"),
      keyLen = config.getInt("keyLen")
    )
  }

  def apply(salt: Array[Byte], iterationCount: Int, keyLen: Int): PasswordHash = {
    new PasswordHash(salt.toList.toArray, iterationCount, keyLen)
  }
}
