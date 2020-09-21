package franz.db.impl

import franz.IntegrationTest
import franz.db.BaseGleichDBTest
import monix.execution.Scheduler.Implicits.global

import scala.concurrent.Await

trait DocSchemasTest extends BaseGleichDBTest {

  "DocSchemas.insert" should {
    "insert schemas"  taggedAs (IntegrationTest) in {
      val schemas = DocSchemas()
      val collection = nextId()
      schemas.insert(collection, "user", TestData.SomeRecord()).runToFuture.futureValue
      schemas.insert(collection, "user", TestData.DifferentRecord()).runToFuture.futureValue
      val readBack = schemas.schemasFor(collection, 10).toListL.runToFuture.futureValue
      readBack.size shouldBe 2
    }
    "not insert duplicate"  taggedAs (IntegrationTest) in {
      val schemas = DocSchemas()
      val collection = nextId()
      val recordv1 = TestData.SomeRecord()
      val recordv2 = TestData.SomeRecord()
      schemas.insert(collection, "user", recordv1).runToFuture.futureValue
      intercept[Exception] {
        Await.result(schemas.insert(collection, "user", recordv2).runToFuture, testTimeout)
      }
    }
  }
}
