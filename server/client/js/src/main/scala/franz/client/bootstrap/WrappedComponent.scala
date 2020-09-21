package franz.client.bootstrap

import org.scalajs.dom.raw.Element
import scalatags.JsDom.all._

/**
 * @param c
 * @param wrap some function to put content around a Component
 */
case class WrappedComponent(c: Component, wrap: Element => Element) extends Component.Base(c) {
  override def render(): Element = wrap(underlying.render()).render
}
