package franz.rest.kafka.routes

import java.util.Base64

import com.typesafe.config.{Config, ConfigFactory}
import kafka4m.producer.RichKafkaProducer
import zio.{Task, UIO, ZIO}

class ProducerOps private(val producer: RichKafkaProducer[String, Array[Byte]]) extends AutoCloseable {

  override def close(): Unit = producer.close()

  val closeTask : UIO[Unit] = Task(close()).fork.ignore

  def push(topic: String, key: String, value: String): Task[RecordMetadataResponse] = push(PublishOne(topic, key, value))

  def push(request: PublishOne): ZIO[Any, Throwable, RecordMetadataResponse] = {
    Task.fromFuture { _ =>
      val bytes = if (request.isBase64) {
        Base64.getDecoder.decode(request.value)
      } else {
        request.value.getBytes("UTF-8")
      }

      producer.sendAsync(
        request.topic,
        request.key,
        bytes,
        partition = request.partition.getOrElse(-1))
    }.map(RecordMetadataResponse.apply)
  }
}

object ProducerOps {
  def apply(rootConfig: Config = ConfigFactory.load()): ProducerOps = {
    implicit val keySerializer = new org.apache.kafka.common.serialization.StringSerializer
    implicit val valueSerializer = new org.apache.kafka.common.serialization.ByteArraySerializer
      val publisher = RichKafkaProducer.forConfig(rootConfig.getConfig("franz.kafka.producer"), keySerializer, valueSerializer)
      new ProducerOps(publisher)
  }
}
