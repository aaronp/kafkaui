package franz.data.query

import eie.io.MD5

/**
 * TODO - encode this in a better way that rich type pimping - perhaps just put separate 'JVM ' and JS type which extend
 * a shared type?
 */
object RichNamedSchema {

  implicit class Install(val schema: NamedSchema) extends AnyVal {
    def md5: String = MD5(toString)
  }

}
