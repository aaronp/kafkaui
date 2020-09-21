package franz.data.index

import franz.data.VersionedRecord.syntax._
import franz.data.{SomeTestClass, VersionedRecord}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class PackageTest extends AnyWordSpec with Matchers {

  "referencesFor" should {
    "return the values associated w/ their ReferenceToValue" in {
      val record: VersionedRecord[SomeTestClass] = SomeTestClass("alpha", 1).versionedRecord(id = "one", userId = "dave")
      val refs: Seq[(String, ReferenceToValue)] = referencesFor("meh", record)

      refs should contain theSameElementsAs List(
        ("alpha" -> ReferenceToValue("meh", Seq("name"), "one", 0)),
        ("1" -> ReferenceToValue("meh", Seq("number"), "one", 0)),
        ("true" -> ReferenceToValue("meh", Seq("truthy"), "one", 0))
      )
    }
  }
}
