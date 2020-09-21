package franz.firestore

import cats.syntax.option._
import franz.data.VersionedRecord.syntax._
import franz.data.{RecordCoords, VersionedRecord}
import io.circe.Json
import io.circe.syntax._
import zio.test.Assertion.equalTo
import zio.test.{DefaultRunnableSpec, assert, suite, testM}

object FSReadTest extends DefaultRunnableSpec {

  def spec = suite("FSRead")(

    testM("read the latest, exact, previous and next values") {
      val collectionName = s"collection-${getClass.getSimpleName}${System.currentTimeMillis()}"

      val data = FSInsertTest.Data("alpha", FSInsertTest.Address("street", "WI"))
      val record1: VersionedRecord[Json] = data.asJson.versionedRecord(id = "abc123", version = 1)
      val record2: VersionedRecord[Json] = data.copy(name = "beta").asJson.versionedRecord(id = "abc123", version = 2)
      val record3: VersionedRecord[Json] = data.copy(name = "gamma").asJson.versionedRecord(id = "abc123", version = 4)
      val differentId: VersionedRecord[Json] = data.copy(name = "not me").asJson.versionedRecord(id = "different", version = 3)

      val underTest = for {
        ins1 <- FSInsert().insert(collectionName, record1)
        ins2 <- FSInsert().insert(collectionName, record2)
        ins3 <- FSInsert().insert(collectionName, record3)
        diff <- FSInsert().insert(collectionName, differentId)
        readPrevious <- FSRead().read(RecordCoords.previous(collectionName, "abc123", 2))
        readNext <- FSRead().read(RecordCoords.next(collectionName, "abc123", 2))
        readTwo <- FSRead().read(RecordCoords(collectionName, "abc123", 2))
        readLatest <- FSRead().read(RecordCoords.latest(collectionName, "abc123"))
        readMissing <- FSRead().read(RecordCoords.latest(collectionName, "missing"))
        _ <- FSDropCollection(collectionName)
      } yield (ins1, ins2, ins3, diff, readPrevious, readNext, readTwo, readLatest, readMissing)

      for {
        (ins1, ins2, ins3, diff, readPrevious, readNext, readTwo, readLatest, readMissing) <- underTest.provideLayer(FSEnv.live)
      } yield {
        assert(ins1.isSuccess)(equalTo(true)) &&
          assert(ins2.isSuccess)(equalTo(true)) &&
          assert(ins3.isSuccess)(equalTo(true)) &&
          assert(diff.isSuccess)(equalTo(true)) &&
          assert(readPrevious)(equalTo(record1.some)) &&
          assert(readNext)(equalTo(record3.some)) &&
          assert(readTwo)(equalTo(record2.some)) &&
          assert(readLatest)(equalTo(record3.some)) &&
          assert(readMissing)(equalTo(None))
      }
    }
  )
}
