package franz.data.crud

import cats.effect.IO
import franz.data.VersionedRecord
import franz.data.VersionedRecord.syntax._
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class VersionedMultiMapTest extends AnyWordSpec with Matchers with GivenWhenThen {

  val versionedCache = for {
    cache <- MultiMap.empty[IO, String, VersionedRecord[String]]
    v1 = "first".versionedRecord(id = "A")
    _ <- cache.insert(v1.id, v1)
    v2 = v1.withData("second").incVersion
    _ <- cache.insert(v2.id, v2)
    v3 = v2.withData("third").incVersion
    _ <- cache.insert(v3.id, v3)
    b1 = "B".versionedRecord(id = "B")
    _ <- cache.insert(b1.id, b1)
    b2 = b1.withData("C").incVersion
    _ <- cache.insert(b2.id, b2)
  } yield cache


  "VersionedMultiMap.findPrevious" should {
    "find the previous version even if we skipped a couple" in {
      val audit = versionedCache.unsafeRunSync().versioned
      audit.findPrevious.read("A" -> 0).unsafeRunSync shouldBe None
      val Some(a) = audit.findPrevious.read("A" -> 1).unsafeRunSync
      val Some(b) = audit.findPrevious.read("A" -> 2).unsafeRunSync
      val Some(c) = audit.findPrevious.read("A" -> 3).unsafeRunSync
      val Some(last)  = audit.findPrevious.read("A" -> 10).unsafeRunSync
      a.data shouldBe "first"
      b.data shouldBe "second"
      c.data shouldBe "third"
      last.data shouldBe "third"
    }
  }
  "VersionedMultiMap.findVersion" should {
    "return the penultimate record" in {
      val audit = versionedCache.unsafeRunSync().versioned
      val version1 = audit.findVersion(1)

      val Some(a) = version1.read("A").unsafeRunSync()
      val Some(b) = version1.read("B").unsafeRunSync()
      version1.read("C").unsafeRunSync() shouldBe None

      a.data shouldBe "second"
      a.version shouldBe 1
      b.data shouldBe "C"
      b.version shouldBe 1
    }
  }
  "VersionedMultiMap.versionedWriter" should {

    "Not overwrite data with stale versions" ignore {
      val test = for {
        map <- MultiMap.empty[IO, String, VersionedRecord[String]]
        cache = map.versioned
        writer = cache.versionedWriter[String](false)
        v1 = "first".versionedRecord(id = "A")
        firstResp <- writer.insert(v1)
        v3 = v1.withData("third").incVersion.incVersion
        threeResp <- writer.insert(v3)
        stale = v1.withData("second").incVersion
        stateResponse <- writer.insert(stale)
        different = "different".versionedRecord(id = "B", version = 0)
        differentResponse <- writer.insert(different)
      } yield (cache, firstResp, threeResp, stateResponse, differentResponse)

      val actual = test.unsafeRunSync
      val (cache, InsertSuccess(0, r1), InsertSuccess(2, i2), InvalidDetailedResponse(1, Some(2), None), InsertSuccess(0, diff)) = actual
      i2.version shouldBe 2
      diff.id shouldBe "B"

      cache.findLatestRecord[String].read("A").unsafeRunSync().get.data shouldBe "third"
      cache.findLatestRecord[String].read("B").unsafeRunSync().get.data shouldBe "different"
    }
  }
  "VersionedMultiMap.findVersionReader" should {
    "return the penultimate record" in {
      val audit = versionedCache.unsafeRunSync().versioned
      val versionFinder: ReadRecord.Service[IO, (String, Int), Option[VersionedRecord[String]]] = audit.findVersionReader


      versionFinder.read("A" -> 0).unsafeRunSync().get.data shouldBe "first"
      versionFinder.read("A" -> 1).unsafeRunSync().get.data shouldBe "second"
      versionFinder.read("A" -> 2).unsafeRunSync().get.data shouldBe "third"
      versionFinder.read("A" -> 3).unsafeRunSync() shouldBe None
    }
  }
  "VersionedMultiMap.findPrevious" should {
    "return the penultimate record" in {
      val audit = versionedCache.unsafeRunSync().versioned
      val previous: ReadRecord.Service[IO, (String, Int), Option[VersionedRecord[String]]] = audit.findPrevious

      val Some(a) = previous.read("A" -> 2).unsafeRunSync()
      val Some(b) = previous.read("B" -> 1).unsafeRunSync()
      previous.read("C" -> 1).unsafeRunSync() shouldBe None

      a.data shouldBe "second"
      a.version shouldBe 1
      b.data shouldBe "B"
      b.version shouldBe 0
    }
  }
  "VersionedMultiMap.maxBy" should {
    "be able to act like an audited version record" in {
      val audit = versionedCache.unsafeRunSync().versioned

      val findLatest = audit.findMax(_.version)

      val Some(a) = findLatest.read("A").unsafeRunSync()
      val Some(b) = findLatest.read("B").unsafeRunSync()
      findLatest.read("C").unsafeRunSync() shouldBe None

      a.data shouldBe "third"
      a.version shouldBe 2
      b.data shouldBe "C"
      b.version shouldBe 1
    }
  }
}
