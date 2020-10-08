package franz.rest.kafka.routes

import java.util.UUID

import com.typesafe.config.{Config, ConfigFactory}
import kafka4m.consumer.RichKafkaConsumer
import monix.execution.Scheduler
import monix.execution.schedulers.SchedulerService
import org.apache.kafka.clients.consumer.{ConsumerRecord, KafkaConsumer}
import org.apache.kafka.common.TopicPartition
import zio.{Task, UIO}

import scala.concurrent.Future
import scala.jdk.CollectionConverters._

/**
 * A set of operations we want to perform for reading from kafka
 *
 * @param consumerGroupId
 * @param consumer
 */
final case class ConsumerOps(consumerGroupId: String, consumer: RichKafkaConsumer[ConsumerGroupId, Array[Byte]]) extends AutoCloseable {

  override def close(): Unit = consumer.close()

  def closeTask: UIO[Unit] = UIO(close())

  def assignments(): Task[Set[TopicPartition]] = {
    Task {
      val a: Set[TopicPartition] = consumer.assignments
      val b = consumer.assignmentsAccordingToCallback
      println(
        s"""consumer.assignments:$a
           |consumer.assignmentsAccordingToCallback:$b""".stripMargin)
      a ++ b
    }
  }

  def assign(topics: Set[TopicPartition] = Set.empty): Task[Unit] = {
    Task.fromFuture { _ =>
      consumer.withConsumer { c =>
        c.consumer.assign(topics.asJava)
      }
    }
  }

  def seekTo(offset: Long) = {
    Task.fromFuture { _ =>
      consumer.withConsumer { c =>
        c.seekToOffset(offset)
      }
    }.map(_.get)
  }

  def monixToZIO[A](m: monix.eval.Task[A]): Task[A] = {
    Task.fromFuture { ec =>
      implicit val s = Scheduler(ec)
      m.runToFuture
    }
  }

  /**
   * @param n the number to take
   * @return
   */
  def peek(n: Long): Task[List[ConsumerRecord[ConsumerGroupId, Array[Byte]]]] = {
    take(n).flatMap { list =>
      val count = list.size
      println(s"\tpeek($n) => $count")
      if (count < n) {
        peek(n - count).map(list ++ _)
      } else {
        Task.succeed(list)
      }
    }
  }

  def take(n: Long): Task[List[ConsumerRecord[ConsumerGroupId, Array[Byte]]]] = {
    for {
      batch <- monixToZIO(consumer.poll)
      d8a <- monixToZIO(batch.take(n).toListL)
    } yield d8a
  }

  def withKafkaConsumer[A](thunk: KafkaConsumer[String, Array[Byte]] => A): Future[A] = {
    consumer.withConsumer { c => thunk(c.consumer) }
  }

  private def pauseMonix: monix.eval.Task[Unit] = {
    monix.eval.Task.fromFuture {
      consumer.withConsumer { c =>
        val partitions = c.partitions.map(_.asTopicPartition)
        consumer.consumer.pause(partitions.asJava)
      }
    }
  }

  private def resume(): Task[Unit] = {
    Task {
      consumer.withConsumer { c =>
        val partitions = c.partitions.map(_.asTopicPartition)
        consumer.consumer.resume(partitions.asJava)
      }
    }
  }
}

object ConsumerOps {

  implicit lazy val consumerScheduler: SchedulerService = Scheduler.io("consumers")

  def apply(topicOverrides: Set[Topic] = Set.empty,
            consumerGroupId: ConsumerGroupId = s"consumer-ops-${eie.io.AlphaCounter(UUID.randomUUID())}",
            rootConfig: Config = ConfigFactory.load()
           )(implicit scheduler: Scheduler = consumerScheduler): ConsumerOps = {

    val overrides = ConfigFactory.parseString(
      s"""group.id : "${consumerGroupId}"
         |auto.offset.reset: none""".stripMargin)

    val config = overrides.withFallback(rootConfig.getConfig("franz.kafka.consumer")).resolve

    val keyDeserializer = new org.apache.kafka.common.serialization.StringDeserializer
    val valueDeserializer = new org.apache.kafka.common.serialization.ByteArrayDeserializer

    val consumer: RichKafkaConsumer[ConsumerGroupId, Array[Byte]] = RichKafkaConsumer.forConfig(config, keyDeserializer, valueDeserializer, topicOverrides)
    new ConsumerOps(consumerGroupId, consumer)
  }
}
