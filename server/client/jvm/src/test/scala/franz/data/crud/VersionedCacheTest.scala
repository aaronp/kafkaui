package franz.data.crud

import franz.data.Versioned.syntax._
import franz.data.{HasKey, Versioned}

class VersionedCacheTest extends InsertRecordAssertions {

  implicit val dataKey = HasKey.versionedId[String].map(_.toUpperCase())

  val hasKey = HasKey[Versioned[String], String]

  "VersionedCache.updateState" should {
    "insert any version if there is no previous version" in {
      val first: Versioned[String] = "alpha".versioned(123)

      val state = VersionedCache.updateState[String, Versioned[String]](first, true)

      val (newMap, InsertSuccess(123, readBack)) = state.run(Map.empty).value
      readBack shouldBe first
      newMap shouldBe Map(hasKey.keyFor(first) -> first)
    }

    "accept updates when updating the next version" in {
      val first: Versioned[String] = "alpha".versioned(123)
      val newer = first.inc()
      val state = VersionedCache.updateState(newer, true)
      val key = hasKey.keyFor(first)
      val (newMap, response) = state.run(Map(key -> first)).value
      newMap shouldBe Map(hasKey.keyFor(newer) -> newer)
      response shouldBe InsertSuccess(124, Versioned(124, "alpha")) //123, Versioned(123, "alpha"),
    }

    "reject updates when given too advanced a version" in {
      val first = "alpha".versioned(123)

      val newer = "ALPHA".versioned(200)
      val state = VersionedCache.updateState(newer, true)

      val (newMap, response) = state.run(Map(hasKey.keyFor(first) -> first)).value
      newMap shouldBe Map(hasKey.keyFor(first) -> first)
      response shouldBe InsertResponse.invalidVersion(200)//123,
    }
    "reject older versions from insertion" in {
      val older: Versioned[String] = "alpha".versioned(123)
      val first = older.inc()

      val state = VersionedCache.updateState(older, true)
      val (newMap, response) = state.run(Map(hasKey.keyFor(first) -> first)).value
      newMap shouldBe Map(hasKey.keyFor(first) -> first)

      response shouldBe InsertResponse.invalidVersion(older.version) //first.version,
    }
  }
}
