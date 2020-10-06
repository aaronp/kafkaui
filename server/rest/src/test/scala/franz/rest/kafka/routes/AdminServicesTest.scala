package franz.rest.kafka.routes

import com.typesafe.config.ConfigFactory
import franz.rest.BaseTest

class AdminServicesTest extends BaseTest {

  "KafkaService" should {
    "be able create and read back topics" in {
      val rootCfg = ConfigFactory.load()


      val ks: AdminServices = AdminServices(rootCfg)

      val newTopic = nextTopic()

      val created = ks.createTopic(AdminServices.CreateTopic(newTopic, 2, 3)).value()
      created shouldBe AdminServices.CreateTopic(newTopic, 2, 3)
      val map = ks.topics(true).value()
      map should not be (empty)
      map(newTopic) shouldBe false
    }
  }
}
