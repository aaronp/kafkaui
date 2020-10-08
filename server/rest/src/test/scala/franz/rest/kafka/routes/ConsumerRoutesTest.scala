package franz.rest.kafka.routes

import franz.rest.BaseTest
import franz.rest.kafka.routes.ConsumerRoutesTest._
import zio._

class ConsumerRoutesTest extends BaseTest {

  "ConsumerRoutes" should {

    "layer demo" in {
      val foo = Has(Foo(3))
      val bar = Has(Bar(5))
      val m = ConsumerRoutesTest.Multiply.provide(foo ++ bar)
      m.value() shouldBe 15
    }
    "layer example" in {
      val foo = Has(Foo(3))
      val bar = Has(Bar(5))
      val m = ConsumerRoutesTest.Multiply.provide(foo ++ bar)
      m.value() shouldBe 15
    }
  }
}

object ConsumerRoutesTest {

  case class Foo(x: Int)

  type FooService = Has[Foo]

  case class Bar(y: Int)

  type BarService = Has[Bar]

  val Multiply: ZIO[BarService with FooService, Nothing, Int] = {
    for {
      foo <- ZIO.access[FooService](_.get)
      bar <- ZIO.access[BarService](_.get)
    } yield foo.x * bar.y
  }
  val Multiply1: ZIO[Bar with Foo, Nothing, Int] = {
    for {
      foo <- ZIO.environment[Foo]
      bar <- ZIO.environment[Bar]
    } yield foo.x * bar.y
  }
}
