package franz.rest.kafka.routes

import java.util.UUID

import com.typesafe.config.{Config, ConfigFactory}
import kafka4m.consumer.RichKafkaConsumer
import monix.execution.Scheduler
import monix.execution.schedulers.SchedulerService
import org.apache.kafka.clients.consumer.ConsumerRecord
import zio.{Task, UIO}

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

  def seekTo(offset: Long): Task[Boolean] = Task.fromTry(consumer.seekToOffset(offset))

  /**
   * @param n the number to take
   * @return
   */
  def peek(n: Long): Task[List[ConsumerRecord[ConsumerGroupId, Array[Byte]]]] = {
    for {
      _ <- resume()
      d8a <- Task.fromFuture { ec =>
        implicit val s = Scheduler(ec)
        consumer
          .asObservable(false)
          .take(n)
          .guarantee(pauseMonix)
          .toListL
          .runToFuture
      }
    } yield d8a
  }

  private def pauseMonix: monix.eval.Task[Unit] = {
    monix.eval.Task {
      val partitions = consumer.partitions.map(_.asTopicPartition)
      consumer.consumer.pause(partitions.asJava)
    }
  }

  private def resume(): Task[Unit] = {
    Task {
      val partitions = consumer.partitions.map(_.asTopicPartition)
      consumer.consumer.resume(partitions.asJava)
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
