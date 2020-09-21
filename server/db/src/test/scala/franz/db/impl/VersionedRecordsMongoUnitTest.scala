package franz.db.impl

import com.typesafe.config.ConfigFactory
import franz.IntegrationTest
import franz.data.collectionNameFor
import franz.users.RegisteredUser
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class VersionedRecordsMongoUnitTest extends AnyWordSpec with Matchers {

  "VersionedRecordsMongo.SettingsLookup" should {
    "use the registereduser settings" taggedAs(IntegrationTest) in {
      val lookup = VersionedRecordsMongo.SettingsLookup(ConfigFactory.load())
      val name = collectionNameFor[RegisteredUser]
      val settings = lookup.latestSettingsForName(name)
      withClue(settings.indices.mkString("\n")) {
        settings.indices.size shouldBe 3
      }
    }
  }
}
