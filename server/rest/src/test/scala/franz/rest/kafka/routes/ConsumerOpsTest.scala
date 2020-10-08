package franz.rest.kafka.routes

import franz.rest.{BaseTest, RunningKafka}
import zio.{Has, Task, ZIO}

class ConsumerOpsTest extends BaseTest with RunningKafka {

  "ConsumerOps.peekOnce" should {
    "take some" in {
      val topic = nextTopic()

      val task = Task(ProducerOps()).bracket(_.closeTask) { write =>
        Task(AdminOps()).bracket(_.closeTask) { admin =>
          for {
            _ <- ZIO.foreach((0 until 10).toList) { i =>
              write.push(topic, s"k$i", i.toString)
            }
            r1 <- onPeekRequest(PeekRequest(Set(topic), 5, 3)).provide(Has(admin))
            r2 <- onPeekRequest(PeekRequest(Set(topic), 2, 2)).provide(Has(admin))
            offsets <- admin.listOffsetsForTopics(Set(topic))
          } yield (offsets, r1.records, r2.records)
        }
      }

      val (Seq(offsets), list1, list2) = task.valueFuture().futureValue
      list1.size shouldBe 3
      list2.size shouldBe 2
      list1.map(_.offset) shouldBe List(5, 6, 7)
      list2.map(_.offset) shouldBe List(2, 3)
      offsets.topic shouldBe topic
      offsets.partition shouldBe 0
      offsets.earliest.offset shouldBe 0
      offsets.latest.offset shouldBe 10
    }
  }
  "ConsumerOps" should {
    "be able to read from a specified offset" in {
      val topic = nextTopic()

      def consumeTest(read: ConsumerOps, write: ProducerOps, admin: AdminOps) = {
        for {
          _ <- ZIO.foreach((0 until 100).toList) { i =>
            write.push(topic, s"k$i", i.toString)
          }
          partitionMap <- admin.partitionsForTopic(Set(topic))
          partitions = partitionMap.values.flatMap(_.asTopicPartitions)
          _ <- read.assign(partitions.toSet)
          seek <- read.seekTo(10)
          _ = seek shouldBe true
          firstTwo <- read.peek(2)
          offsets <- admin.listOffsetsForTopics(Set(topic))
        } yield (offsets, firstTwo)
      }

      val task = Task(ConsumerOps(Set(topic))).bracket(_.closeTask) { read =>
        Task(ProducerOps()).bracket(_.closeTask) { write =>
          Task(AdminOps()).bracket(_.closeTask) { admin =>
            consumeTest(read, write, admin)
          }
        }
      }

      val (Seq(offsets), list) = task.value()
      list.size shouldBe 2
      list.map(_.offset()) shouldBe List(10, 11)
      offsets.topic shouldBe topic
      offsets.partition shouldBe 0
      offsets.earliest.offset shouldBe 0
      offsets.latest.offset shouldBe 100
    }
  }
}
