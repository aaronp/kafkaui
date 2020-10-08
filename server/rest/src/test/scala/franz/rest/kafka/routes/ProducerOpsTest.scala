package franz.rest.kafka.routes

import java.util.UUID

import franz.rest.{BaseTest, RunningKafka}
import zio.{Task, URIO, ZIO}

class ProducerOpsTest extends BaseTest with RunningKafka {

  "ProducerServices.push" should {
    "push publish PublishOne values" in {

      val topic = nextTopic()

      def pushThreeValues(svc: ProducerOps): ZIO[Any, Throwable, (RecordMetadataResponse, RecordMetadataResponse, RecordMetadataResponse)] = {
        for {
          a <- svc.push(PublishOne(topic, "key1", "data"))
          b <- svc.push(PublishOne(topic, "key2", "data"))
          c <- svc.push(PublishOne(topic, "key3", "data"))
        } yield (a, b, c)
      }

      // push three values all using the same ProducerServices
      val firstThree = Task(ProducerOps()).bracket(p => URIO(p.close()), pushThreeValues)

      // push one value with a new instance
      val afterClose = Task(ProducerOps()).bracket(p => URIO(p.close())) { svc =>
        svc.push(PublishOne(topic, "four", "more data"))
      }
      val (RecordMetadataResponse(TopicKey(`topic`, 0), 0, ts1, 4, 4),
      RecordMetadataResponse(TopicKey(`topic`, 0), 1, ts2, 4, 4),
      RecordMetadataResponse(TopicKey(`topic`, 0), 2, ts3, 4, 4)) = firstThree.value()

      val RecordMetadataResponse(TopicKey(`topic`, 0), 3, ts4, 4, 9) = afterClose.value()

      ts1 should be <= ts2
      ts2 should be <= ts3
      ts3 should be <= ts4
    }
  }
}