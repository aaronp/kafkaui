package franz.data.crud

import cats.effect.IO
import franz.data.QueryRange.Default
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class CacheTest extends AnyWordSpec with Matchers with GivenWhenThen {

  "Cache.getOrCreate" should {
    "not replace existing entries" in {
      var instances = 0
      case class Fake(id: String) {
        val count: Int = instances + 1
        instances = count
      }
      val test = for {
        cache <- Cache.empty[IO, String, Fake]
        a <- cache.getOrCreate("foo", Fake("first"))
        b <- cache.getOrCreate("foo", Fake("first"))
        c <- cache.insert("foo", Fake("second"))
        d <- cache.read("foo")
      } yield (a, b, c, d)

      val ((true, a), (false, b), true, Some(d)) = test.unsafeRunSync()
      instances shouldBe 2
      a.id shouldBe "first"
      a.count shouldBe 1
      withClue("The second 'Fake' should not have been created") {
        b.id shouldBe "first"
        b.count shouldBe 1
      }
      d.id shouldBe "second"
      d.count shouldBe 2
    }
  }
  "Cache" should {
    "crud" in {

      val app = for {
        cache <- Cache.empty[IO, String, Int]
        _ <- cache.insertService.insert("foo", 1)
        _ <- cache.insertService.insert("bar", 2)
        _ <- cache.insertService.insert("fizz", 3)
        _ <- cache.insertService.insert("foo", 4)
        _ <- cache.deleteService.delete("bar")
        foo <- cache.readService.read("foo")
        bar <- cache.readService.read("bar")
        list <- cache.listService.list(Default)
      } yield {
        (foo, bar, list)
      }

      val (Some(4), None, list) = app.unsafeRunSync()

      list.toSeq should contain only(("foo", 4), ("fizz", 3))
    }
  }
}
