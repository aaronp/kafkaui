package franz.rest.kafka.routes

import java.time.ZonedDateTime

import franz.rest.{BaseTest, RunningKafka}
import org.apache.kafka.clients.consumer.ConsumerRecord
import zio.{Task, ZIO}

class ConsumerOpsTest extends BaseTest with RunningKafka {

  "ConsumerOps" should {
    "work" in {
      val topic = nextTopic()

      def consumeTest(read: ConsumerOps, write: ProducerOps, admin: AdminOps): Task[List[ConsumerRecord[ConsumerGroupId, Array[Byte]]]] = {
        for {
          _ <- ZIO.foreach((0 until 100).toList) { i =>
            write.push(topic, s"k$i", i.toString)
          }
          partitionMap <- admin.partitionsForTopic(Set(topic))
          _ = println(partitionMap)
          partitions = partitionMap.values.flatMap(_.asTopicPartitions)
          _ <- read.assign(partitions.toSet)
          assignments <- read.assignments()
            .tap(partitions => Task(println(s" assignments at ${ZonedDateTime.now()}: ${partitions}")))
            .delay(java.time.Duration.ofSeconds(1))
            .repeatWhile(_.isEmpty)
            .provide(rt.environment)
          read10 <- read.seekTo(10)
          _ = read10 shouldBe true
          firstTwo <- read.peek(2)
          stats1 <- admin.consumerGroupStats()
          groupsList1 <- admin.listConsumerGroups()
          offsets1 <- admin.listOffsetsForTopics(Set(topic))
          groups1 <- admin.describeConsumerGroups(Set(read.consumerGroupId), true)
          _ = println(s"assignments is $assignments")
          read80 <- read.seekTo(80)
          stats2 <- admin.consumerGroupStats()
          groupsList2 <- admin.listConsumerGroups()
          offsets2 <- admin.listOffsetsForTopics(Set(topic))
          groups2 <- admin.describeConsumerGroups(Set(read.consumerGroupId), true)
          _ = read80 shouldBe true
          _ = println("!" * 100)
          _ = println("-" * 100)
          _ = println("!" * 100)
          secondTwo <- read.peek(2)
        } yield {
          import io.circe.syntax._
          println(
            s"""
               |stats1:
               |${stats1.asJson.spaces2}
               |
               |groupsList1:
               |${groupsList1.asJson.spaces2}
               |
               |offsets1:
               |${offsets1.asJson.spaces2}
               |
               |groups1:
               |${groups1.asJson.spaces2}
               |
               |----------------------------------------------------------------------------------------------------
               |
               |stats2:
               |${stats2.asJson.spaces2}
               |
               |groupsList2:
               |${groupsList2.asJson.spaces2}
               |
               |offsets2:
               |${offsets2.asJson.spaces2}
               |
               |groups2:
               |${groups2.asJson.spaces2}
               |
               |""".stripMargin)
          firstTwo ++ secondTwo
        }
      }

      val testTask = Task(ConsumerOps(Set(topic))).bracket(_.closeTask) { read =>
        Task(ProducerOps()).bracket(_.closeTask()) { write =>
          Task(AdminOps()).bracket(_.closeTask) { admin =>
            consumeTest(read, write, admin)
          }
        }
      }.value()
      testTask.foreach(println)
      testTask.size shouldBe 4
    }
  }
}
