package franz.data.crud

import cats.Monad
import franz.data.{VersionedJson, VersionedJsonResponse}
import franz.io.Run
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 */
trait InsertRecordAssertions extends AnyWordSpec with Matchers {

  def detailedInsertTests[F[_] : Run : Monad, A <: InsertRecord[F, VersionedJson, VersionedJsonResponse]](underTest: A, testData: InsertRecordTestData = InsertRecordTestData()): (A, InsertRecordTestData) = {
    val testF = testData.runDetailed(underTest)
    val actual = Run[F].run(testF)
    actual shouldBe testData.expectedInsertResults
    (underTest, testData)
  }


  def verifyInsert[F[_] : Run : Monad, A <: InsertRecord[F, VersionedJson, VersionedJsonResponse]](underTest: A, testData: InsertRecordTestData = InsertRecordTestData()) = {
    val testF = testData.runDetailed(underTest)
    val actual = Run[F].run(testF)
    if (actual != testData.expectedInsertResults) {
      actual.size shouldBe testData.expectedInsertResults.size
      testData.expectedInsertResults.zip(actual).zipWithIndex.map {
        case ((expt, acl), i) =>
          withClue(s"Expected result $i failed") {
            expt shouldBe acl
          }
      }
    } else {
      actual shouldBe testData.expectedInsertResults
    }
    (underTest, testData)
  }
}
