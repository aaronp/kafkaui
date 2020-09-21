package franz.data.crud

import java.util.concurrent.atomic.AtomicLong

import cats.Monad
import cats.data.State
import cats.implicits._
import franz.data.{VersionedJson, VersionedJsonResponse, VersionedRecord}

object InsertRecordTestData {
  private val idCounter = new AtomicLong(System.currentTimeMillis())

  def apply() = new InsertRecordTestData(s"id${idCounter.incrementAndGet()}", s"id${idCounter.incrementAndGet()}")
}

case class InsertRecordTestData(id1: String, id2: String) {
  require(id1 != id2)

  val recordAV1 = VersionedRecord("original", id = id1, created = 123, version = 0).mapToJson
  val recordAV2 = VersionedRecord("updated", id = id1, created = 124, version = 1).mapToJson
  val recordB1 = VersionedRecord("different 1", id = id2, created = 125, version = 0).mapToJson
  val recordB2 = VersionedRecord("different 2", id = id2, created = 126, version = 1).mapToJson
  val recordB3 = VersionedRecord("different 3", id = id2, created = 127, version = 2).mapToJson

  val expectedInsertResults = List(
    InsertSuccess(0, recordAV1),
    InsertSuccess(1, recordAV2),
    InsertSuccess(0, recordB1),
    InsertSuccess(1, recordB2),
    InvalidDetailedResponse(0, None),
    InvalidDetailedResponse(1, None),
    InsertSuccess(2, recordB3)
  )

  def runDetailed[F[_] : Monad](service: InsertRecord[F, VersionedJson, VersionedJsonResponse]): F[List[VersionedJsonResponse]] = {
    insertState[F, VersionedJsonResponse].runA(service).value
  }


  def insertState[F[_] : Monad, A]: State[InsertRecord[F, VersionedJson, A], F[List[A]]] = {
    State { underTest: InsertRecord[F, VersionedJson, A] =>
      val svc = underTest.insertService
      val insert = (svc.insert _)

      val task = for {
        create1 <- insert(recordAV1)
        update1 <- insert(recordAV2)
        create2 <- insert(recordB1)
        update2 <- insert(recordB2)
        earlierShouldFail <- insert(recordB1)
        sameShouldFail <- insert(recordB2)
        b3Success <- insert(recordB3)
      } yield List(create1, update1, create2, update2, earlierShouldFail, sameShouldFail, b3Success)

      (underTest, task)
    }
  }
}
