package franz.rest.kafka.routes

import org.apache.kafka.clients.admin.ConsumerGroupListing
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition

import scala.util.Try

case class ConsumerGroupEntry(groupId: String, isSimpleConsumerGroup: Boolean, status: Option[String])

object ConsumerGroupEntry {
  implicit val codec = io.circe.generic.semiauto.deriveCodec[ConsumerGroupEntry]

  def apply(value: ConsumerGroupListing): ConsumerGroupEntry = {
    ConsumerGroupEntry(value.groupId(), value.isSimpleConsumerGroup, Try(value.state.get).toOption.map(_.name()))
  }
}

case class OffsetMetadata(offset: Long, metadata: String)

object OffsetMetadata {
  implicit val codec = io.circe.generic.semiauto.deriveCodec[OffsetMetadata]

  def apply(value: OffsetAndMetadata) = {
    new OffsetMetadata(value.offset(), value.metadata())
  }
}

case class TopicKey(topic: String, partition: Int)

object TopicKey {
  implicit val codec = io.circe.generic.semiauto.deriveCodec[TopicKey]

  def apply(value: TopicPartition): TopicKey = {
    TopicKey(value.topic, value.partition)
  }
}

case class PublishOne(topic: String, key: String, value: String, partition: Option[Int] = None, isBase64 : Boolean = false)

object PublishOne {
  implicit val codec = io.circe.generic.semiauto.deriveCodec[PublishOne]
}

case class RecordMetadataResponse(topicPartition: TopicKey,
                                  offset: Long,
                                  timestamp: Long,
                                  serializedKeySize: Int,
                                  serializedValueSize: Int)

object RecordMetadataResponse {
  implicit val codec = io.circe.generic.semiauto.deriveCodec[RecordMetadataResponse]

  def apply(value: RecordMetadata): RecordMetadataResponse = {
    RecordMetadataResponse(
      topicPartition = TopicKey(value.topic(), value.partition()),
      offset = value.offset,
      timestamp = value.timestamp(),
      serializedKeySize = value.serializedKeySize(),
      serializedValueSize = value.serializedValueSize()
    )
  }
}