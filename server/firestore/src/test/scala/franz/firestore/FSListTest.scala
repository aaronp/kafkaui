package franz.firestore

import franz.data.QueryRange
import franz.data.VersionedRecord.syntax._
import io.circe.syntax._
import zio.ZIO
import zio.test.Assertion.equalTo
import zio.test.{DefaultRunnableSpec, assert, suite, testM}

object FSListTest extends DefaultRunnableSpec {

  def spec = suite("FSListIds")(

    testM("list all a collections records") {
      val collectionName = s"collection-${getClass.getSimpleName}${System.currentTimeMillis()}"

      val records = (0 until 10).map { i =>
        val data = FSInsertTest.Data(s"value${i}", FSInsertTest.Address("street", "WI"))
        val record = data.asJson.versionedRecord(id = s"${i}")
        FSInsert().insert(collectionName, record)
      }

      val underTest = for {
        _ <- ZIO.foreachPar(records)(identity)
        head <- FSList.listIds(collectionName).list(QueryRange(0, 1))
        second <- FSList.listIds(collectionName).list(QueryRange(1, 1))
        three <- FSList.listIds(collectionName).list(QueryRange(1, 3))
        none <- FSList.listIds(collectionName).list(QueryRange(11, 10))
        _ <- FSDropCollection(collectionName)
      } yield (head, second, three, none)

      for {
        (head, second, three, none) <- underTest.provideLayer(FSEnv.live)
      } yield {
        assert(head)(equalTo(List("0"))) &&
          assert(second)(equalTo(List("1"))) &&
          assert(three)(equalTo(List("1", "2", "3"))) &&
          assert(none)(equalTo(Nil))
      }
    }
  )
}
