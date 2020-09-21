package franz.data.crud

import cats.effect.IO
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import franz.data.QueryRange.Default

class MultiMapTest extends AnyWordSpec with Matchers with GivenWhenThen {

  "MultiMap" should {
    "crud" in {
      val app: IO[(Seq[Int], Seq[Int], Map[String, Seq[Int]])] = for {
        cache <- MultiMap.empty[IO, String, Int]
        _ <- cache.insert("foo", 1)
        _ <- cache.insert("bar", 2)
        _ <- cache.insert("shouldBeRemoved", 100)
        _ <- cache.insert("shouldBeRemoved", 200)
        _ <- cache.insert("fizz", 3)
        _ <- cache.insert("fizz", 4)
        _ <- cache.insert("fizz", 5)
        _ <- cache.insert("foo", 4)
        _ <- cache.delete("fizz", 4)
        _ <- cache.delete("fizz", 40)
        _ <- cache.delete("shouldBeRemoved", 100)
        _ <- cache.delete("shouldBeRemoved", 200)
        foo <- cache.readService.read("foo")
        bar <- cache.readService.read("bar")
        list <- cache.listService.list(Default)
      } yield {
        (foo, bar, list)
      }

      val (foo, bar, map) = app.unsafeRunSync()

      foo should contain only(1, 4)
      bar should contain only (2)
      map.keySet should contain only("foo", "bar", "fizz")
      map("foo") should contain only(1, 4)
      map("bar") should contain only (2)
      map("fizz") should contain only(3, 5)
    }
  }

}
