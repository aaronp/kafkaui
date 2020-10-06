package franz.rest

import com.typesafe.config.ConfigFactory

class KafkaServiceTest extends BaseTest {

  "KafkaService" should {
    "be able create and read back topics" in {
      val rootCfg = ConfigFactory.load()


      val ks: KafkaService = KafkaService(rootCfg)

      val newTopic = nextTopic()

      val created = rt.unsafeRun(ks.createTopic(KafkaService.CreateTopic(newTopic, 2, 3)))
      created shouldBe KafkaService.CreateTopic(newTopic, 2, 3)
      val map = rt.unsafeRun(ks.topics(true))
      map should not be (empty)
      map(newTopic) shouldBe false
    }
  }
}
