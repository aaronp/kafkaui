package franz.data.diff

import franz.data.SomeTestClass
import franz.data.VersionedRecord.syntax._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class DiffTest extends AnyWordSpec with Matchers {

  "Diff.Result" should {
    "diff two records" in {
      val d0 = Diff.Result.of(SomeTestClass().versionedRecord(id = "a", created = 1), SomeTestClass(name = "changed", number = 4).versionedRecord(id = "a", created = 1, version = 2))
      d0.formatted should contain only(
        "data.name => [anon, changed]",
        "version => [0, 2]",
        "data.number => [1234, 4]"
      )
    }
  }
}
