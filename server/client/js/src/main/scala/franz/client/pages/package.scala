package franz.client

import scala.language.implicitConversions

package object pages {
  def uniqueId(): String = franz.client.js.uniqueId()
}
