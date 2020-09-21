package franz.client.bootstrap

import cats.data.{NonEmptyChain, Validated}
import cats.implicits._
import donovan.json.JPath
import franz.client.js.JsonAsInt
import franz.data.JsonAtPath
import franz.data.index.StemOp
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}
import org.scalajs.dom.raw.Element
import scalatags.JsDom.all.{`class`, div, h4, span, _}

import scala.reflect.ClassTag

/**
 * A form component
 */
trait Component {

  /**
   * @return element for this component
   */
  def render(): Element

  /**
   * @return the value of this component as a validated
   */
  def validated(): Validated[NonEmptyChain[Component.Error], Json]

  /**
   * Update this component with the given json
   *
   * @param value
   */
  def update(value: Json): Unit

  /**
   * put this component under a json path. e.g. if this component produces the json 'a.b : c', then 'nested(foo)'
   * will produce a component that spits out 'foo.a.b : c'
   *
   * @param under   the first element of the path
   * @param theRest the jpath
   * @return a nested component
   */
  final def nested(under: String, theRest: String*): Component.Nested = {
    Component.Nested(under :: theRest.toList, this)
  }

  /**
   * @param test the json check
   * @return a validating component
   */
  final def flatMap[B: Encoder](test: Json => Validated[NonEmptyChain[Component.Error], B]): Component.FlatMap[B] = {
    Component.FlatMap[B](this, test)
  }

  /**
   * @return a component which verifies its contents as a number
   */
  final def numeric: Component.FlatMap[Int] = flatMap[Int](JsonAsInt.apply)

  final def map(transform: Json => Json) = {
    Component.Map(this, transform)
  }

  /**
   * Transform the json update
   *
   * @param transform
   * @return a component which transforms the incoming json
   */
  final def contramap(transform: Json => Json) = {
    Component.ContraMap(this, transform)
  }

  final def typed[A: Encoder : Decoder : ClassTag]: Component.Typed[A] = {
    Component.Typed[A](this, implicitly[ClassTag[A]].runtimeClass.getSimpleName)
  }

  def wrap(thunk: Element => Element): WrappedComponent = {
    WrappedComponent(this, thunk)
  }

  def inLabeledPanel(text: String): WrappedComponent = {
    wrap { inner =>
      div(`class` := "panel panel-default")(
        div(`class` := "panel-heading")(h4(text)),
        div(`class` := "panel-body")(inner)
      ).render
    }
  }
}

object Component {

  def text(label: String, placeholderValue: String = "") = TextInput(label, placeholderValue)

  def const(result: Validated[NonEmptyChain[Error], Json], elm: Element = span().render): Component = new Component {
    override def render(): Element = elm

    override def validated(): Validated[NonEmptyChain[Error], Json] = result

    override def update(value: Json): Unit = {}
  }

  def const(result: String): Component = const(Json.fromString(result))

  def const(result: Json): Component = const(result.validNec)

  def const(html: Element): Component = const(Json.Null.validNec, elm = html)

  def err(error: Error): Component = const(error.invalidNec)

  sealed trait Error {
    def message: String
  }

  object Error {
    def apply(msg: String): Error = GeneralError(msg)

    def apply(fieldError: FormField.Error): FieldError = {
      new FieldError(None, fieldError)
    }

    def apply(fieldErrors: NonEmptyChain[FormField.Error]): NonEmptyChain[Error] = {
      fieldErrors.map { fldErr =>
        FieldError(None, fldErr)
      }
    }
  }

  case class GeneralError(override val message: String) extends Error

  case class FieldError(component: Option[Component], error: FormField.Error) extends Error {
    override def message: String = error.message
  }

  case class Typed[A: Encoder : Decoder](c: Component, name: String) extends Component.Base(c) {
    override def validated(): Validated[NonEmptyChain[Error], Json] = validatedTuple.map(_._2)

    def validatedTyped(): Validated[NonEmptyChain[Error], A] = validatedTuple.map(_._1)

    /**
     *
     * @param f a function which will always be valid in transforming the A into a B
     * @tparam B
     * @return
     */
    def flatMapTyped[B: Encoder](f: A => B): FlatMap[B] = {
      def thunk(json: Json): Validated[NonEmptyChain[Error], B] = decode(json).leftMap(throwableAsErr).map(f)
      Component.FlatMap[B](this, thunk)
    }

    /**
     * flat-maps the typed result 'A' into a Validated[...] of type B
     *
     * @param f a function which transforms the A into a validated B, so-long as the 'B' can be jsonificated
     * @tparam B the validated result type
     * @return a component using the flatmap function
     */
    def flatMapTypedValidated[B: Encoder](f: A => Validated[NonEmptyChain[Error], B]) = {
      def thunk(json: Json): Validated[NonEmptyChain[Error], B] = decode(json).leftMap(throwableAsErr).andThen(f)

      Component.FlatMap[B](this, thunk)
    }

    private def decode(json: Json): Validated[Throwable, A] = Validated.fromTry(json.as[A].toTry)

    private def throwableAsErr(exp: Throwable): NonEmptyChain[Error] = NonEmptyChain.one(Component.Error(s"Couldn't decode: $exp"))

    def validatedTuple(): Validated[NonEmptyChain[Error], (A, Json)] = {
      underlying.validated().andThen { json =>
        decode(json).leftMap(throwableAsErr).map(a => (a, json))
      }
    }

    protected def updateDefault(value: Json): Unit = super.update(value)

    override def update(value: Json): Unit = {
      value.as[A] match {
        case Left(_) => updateDefault(value)
        case Right(typed) => updateTyped(value, typed)
      }
    }

    /**
     * subclasses could use this as a convenience
     * @param originalJson
     * @param value
     */
    def updateTyped(originalJson : Json, value: A): Unit = {
      updateDefault(originalJson)
    }
  }

  case class FlatMap[B: Encoder](c: Component, check: Json => Validated[NonEmptyChain[Component.Error], B]) extends Component.Base(c) {
    override def validated(): Validated[NonEmptyChain[Component.Error], Json] = {
      validatedTyped().map(_.asJson)
    }

    def validatedTyped(): Validated[NonEmptyChain[Component.Error], B] = {
      underlying.validated().andThen(check)
    }
  }

  /** convert the 'update' json
   *
   * @param c     the wrapped component
   * @param thunk the 'update' json conversion
   */
  case class ContraMap(c: Component, thunk: Json => Json) extends Component.Base(c) {
    override def update(value: Json): Unit = super.update(thunk(value))
  }

  case class Map(c: Component, thunk: Json => Json) extends Component.Base(c) {
    override def validated(): Validated[NonEmptyChain[Error], Json] = {
      super.validated().map(thunk)
    }
  }


  /**
   * A delegate class, indended to be overridden
   *
   * @param underlying the wrapped component
   * @tparam C the wrapped type
   */
  abstract class Base[C <: Component](val underlying: C) extends Component {
    override def render(): Element = underlying.render()

    override def validated(): Validated[NonEmptyChain[Error], Json] = underlying.validated()

    override def update(value: Json): Unit = underlying.update(value)
  }

  /**
   * A delegate component, intended to be extended, which can swap out its underlying component
   */
  class Delegate extends Component {
    protected var underlying: Component = Component.const("")

    override def render(): Element = underlying.render()

    override def validated(): Validated[NonEmptyChain[Error], Json] = underlying.validated()

    override def update(value: Json): Unit = underlying.update(value)
  }

  /**
   * nest this json under the jsonPath
   *
   * @param jsonPath the sub-path
   * @param nested   the underlying component
   */
  case class Nested(jsonPath: List[String], nested: Component) extends Component {
    override def render(): Element = nested.render()

    override def update(value: Json): Unit = {
      val nestedValue = JPath.forParts(jsonPath).apply(value).getOrElse(Json.Null)
      nested.update(nestedValue)
    }

    override def validated(): Validated[NonEmptyChain[Component.Error], Json] = {
      nested.validated().map { json =>
        JsonAtPath(jsonPath, json)
      }
    }
  }

}
