package franz.rest.kafka.routes

import com.typesafe.config.ConfigFactory
import franz.rest.{BaseTest, RunningKafka}

class AdminOpsTest extends BaseTest with RunningKafka  {

  "KafkaService" should {
    "be able create and read back topics" in {
      val rootCfg = ConfigFactory.load()


      val ks: AdminOps = AdminOps(rootCfg)

      val newTopic = nextTopic()

      val created = ks.createTopic(AdminOps.CreateTopic(newTopic, 2, 3)).value()
      created shouldBe AdminOps.CreateTopic(newTopic, 2, 3)
      val map = ks.topics(true).value()
      map should not be (empty)
      map(newTopic) shouldBe false
    }
  }
}
