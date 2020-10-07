package franz.rest.kafka.routes

import java.util.UUID

import com.typesafe.config.{Config, ConfigFactory}
import kafka4m.consumer.RichKafkaConsumer
import monix.execution.Scheduler
import monix.execution.schedulers.SchedulerService
import zio.Task

case class ConsumerOps(consumerGroupId: String, consumer: RichKafkaConsumer[ConsumerGroupId, Array[Byte]]) {

  def seekTo(offset : Long) = Task.fromTry(consumer.seekToOffset(offset))
}

object ConsumerOps {

  implicit lazy val consumerScheduler: SchedulerService = Scheduler.io("consumers")

  def apply(consumerGroupId: String = s"consumer-ops-${eie.io.AlphaCounter(UUID.randomUUID())}",
            rootConfig: Config = ConfigFactory.load()
           )(implicit scheduler: Scheduler = consumerScheduler) = {

    val overrides = ConfigFactory.parseString(
      s"""group.id : "${consumerGroupId}"
         |auto.offset.reset: none""".stripMargin)

    val config = overrides.withFallback(rootConfig.getConfig("franz.kafka.consumer")).resolve

    val keyDeserializer = new org.apache.kafka.common.serialization.StringDeserializer
    val valueDeserializer = new org.apache.kafka.common.serialization.ByteArrayDeserializer

    ConsumerOps(consumerGroupId, RichKafkaConsumer.forConfig(config, keyDeserializer, valueDeserializer))
  }
}
