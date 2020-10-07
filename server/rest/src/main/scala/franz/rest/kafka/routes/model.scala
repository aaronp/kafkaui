package franz.rest.kafka.routes

import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo
import org.apache.kafka.clients.admin._
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.metrics.KafkaMetric
import org.apache.kafka.common._

import scala.jdk.CollectionConverters._
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

case class TopicKey(topic: String, partition: Int) {
  def asJava: TopicPartition = new TopicPartition(topic, partition)
}

object TopicKey {
  implicit val codec = io.circe.generic.semiauto.deriveCodec[TopicKey]

  def apply(value: TopicPartition): TopicKey = {
    TopicKey(value.topic, value.partition)
  }
}

case class PublishOne(topic: String, key: String, value: String, partition: Option[Int] = None, isBase64: Boolean = false)

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

final case class NodeDesc(id: Int, idString: String, host: String, port: Int, rack: Option[String])

object NodeDesc {
  def apply(value: Node): NodeDesc = {
    new NodeDesc(
      value.id(),
      value.idString(),
      value.host(),
      value.port(),
      Option(value.rack()),
    )
  }

  implicit val codec = io.circe.generic.semiauto.deriveCodec[NodeDesc]
}

final case class TopicPartitionInfoDesc(partition: Int, leader: NodeDesc, replicas: Seq[NodeDesc], isr: Seq[NodeDesc])

object TopicPartitionInfoDesc {
  def apply(value: TopicPartitionInfo) = {
    new TopicPartitionInfoDesc(value.partition(),
      NodeDesc(value.leader()),
      value.replicas().asScala.map(NodeDesc.apply).toSeq,
      value.isr().asScala.map(NodeDesc.apply).toSeq
    )
  }

  implicit val codec = io.circe.generic.semiauto.deriveCodec[TopicPartitionInfoDesc]
}

final case class TopicDesc(name: String,
                           isInternal: Boolean,
                           partitions: Seq[TopicPartitionInfoDesc],
                           authorizedOperations: Set[String])

object TopicDesc {
  def apply(value: TopicDescription): TopicDesc = {
    new TopicDesc(value.name(), value.isInternal,
      value.partitions().asScala.map(TopicPartitionInfoDesc.apply).toSeq,
      value.authorizedOperations().asScala.map(_.name()).toSet)
  }

  implicit val codec = io.circe.generic.semiauto.deriveCodec[TopicDesc]
}

final case class DescribeCluster(nodes: Seq[NodeDesc],
                                 controller: NodeDesc,
                                 clusterId: String,
                                 authorizedOperations: Set[String])

object DescribeCluster {
  implicit val codec = io.circe.generic.semiauto.deriveCodec[DescribeCluster]
}

final case class MetricKey(name: String,
                           group: String,
                           description: String,
                           tags: Map[String, String])

object MetricKey {
  implicit val codec = io.circe.generic.semiauto.deriveCodec[MetricKey]

  def apply(value: MetricName): MetricKey = {
    new MetricKey(
      value.name(),
      value.group(),
      value.description(),
      value.tags().asScala.toMap
    )
  }
}

object Metric {
  def apply(value: KafkaMetric): String = {
    Try(value.metricValue()).getOrElse("").toString
  }
}

final case class CreatePartitionRequest(newPartitions: Map[String, UpdatedPartition], validateOnly: Boolean)

object CreatePartitionRequest {
  implicit val codec = io.circe.generic.semiauto.deriveCodec[CreatePartitionRequest]
}

final case class UpdatedPartition(totalCount: Int, newAssignments: List[List[Int]] = Nil) {
  def asNewPartitions: NewPartitions = {
    newAssignments match {
      case Nil => NewPartitions.increaseTo(totalCount)
      case list =>
        val jList = list.map(_.map(Integer.valueOf).asJava).asJava
        NewPartitions.increaseTo(totalCount, jList)
    }
  }
}

object UpdatedPartition {
  implicit val codec = io.circe.generic.semiauto.deriveCodec[UpdatedPartition]
}


case class ConsumerGroupMember(consumerId: String,
                               groupInstanceId: Option[String],
                               clientId: String,
                               host: String,
                               assignment: Set[TopicKey])

object ConsumerGroupMember {
  def apply(value: MemberDescription): ConsumerGroupMember = {
    ConsumerGroupMember(
      value.consumerId,
      Try(value.groupInstanceId.get()).toOption,
      value.clientId(),
      value.host(),
      value.assignment().topicPartitions().asScala.map(TopicKey.apply).toSet
    )
  }
}

case class ConsumerGroupDesc(groupId: String,
                             isSimpleConsumerGroup: Boolean,
                             members: List[ConsumerGroupMember],
                             partitionAssignor: String,
                             state: String,
                             coordinator: NodeDesc,
                             authorizedOperations: Set[String]
                            )

object ConsumerGroupDesc {
  implicit val codec = io.circe.generic.semiauto.deriveCodec[ConsumerGroupDesc]

  def apply(value: ConsumerGroupDescription): ConsumerGroupDesc = {
    ConsumerGroupDesc(
      value.groupId,
      value.isSimpleConsumerGroup,
      value.members().asScala.map(ConsumerGroupMember.apply).toList,
      value.partitionAssignor(),
      value.state().name(),
      NodeDesc(value.coordinator()),
      value.authorizedOperations().asScala.map(_.name()).toSet
    )
  }
}

sealed trait Offset {
  def asJava: OffsetSpec = {
    this match {
      case Latest => OffsetSpec.LatestSpec
      case Earliest => OffsetSpec.EarliestSpec
      case Timestamp(at) => new OffsetSpec.TimestampSpec(at)
    }
  }
}

case object Latest extends Offset

case object Earliest extends Offset

case class Timestamp(timestamp: Long) extends Offset

object Timestamp {
  implicit val codec = io.circe.generic.semiauto.deriveCodec[Timestamp]
}

object Offset {
  def latest = Latest

  def earliest = Earliest

  import io.circe.generic.extras.semiauto._

  implicit val codec = deriveEnumerationCodec[Offset]
}

final case class ReadPositionAt(topicPartition: TopicKey, at: Offset)

object ReadPositionAt {
  implicit val codec = io.circe.generic.semiauto.deriveCodec[ReadPositionAt]
}

final case class ListOffsetsRequest(topics: List[ReadPositionAt], readUncommitted: Boolean) {
  def asJavaMap = {
    val map = topics.foldLeft(Map[TopicPartition, OffsetSpec]()) {
      case (map, next) => map.updated(next.topicPartition.asJava, next.at.asJava)
    }
    map.asJava
  }

  def options: ListOffsetsOptions = {
    readUncommitted match {
      case true =>
        new ListOffsetsOptions(IsolationLevel.READ_UNCOMMITTED)
      case false =>
        new ListOffsetsOptions(IsolationLevel.READ_COMMITTED)
    }
  }
}

object ListOffsetsRequest {
  implicit val codec = io.circe.generic.semiauto.deriveCodec[ListOffsetsRequest]
}

final case class OffsetRange(topic: TopicKey, earliest: OffsetInfo, latest: OffsetInfo)

object OffsetRange {
  implicit val codec = io.circe.generic.semiauto.deriveCodec[OffsetRange]
}


object TopicOffsetsResponse {
  def apply(
             earliestResponse: Seq[ListOffsetsEntry],
             latestResponse: Seq[ListOffsetsEntry]
           ): Seq[OffsetRange] = {
    val latestByKey = latestResponse.map { e =>
      e.topic -> e.offset
    }.toMap.ensuring(_.size == latestResponse.size)

    earliestResponse.map { e =>
      latestByKey.get(e.topic) match {
        case Some(latest) => OffsetRange(e.topic, e.offset, latest)
        case None => OffsetRange(e.topic, e.offset, OffsetInfo(-1, -1, None))
      }
    }
  }
}

final case class ListOffsetsEntry(topic: TopicKey, offset: OffsetInfo)

object ListOffsetsEntry {
  implicit val codec = io.circe.generic.semiauto.deriveCodec[ListOffsetsEntry]

  def apply(value: Iterable[(TopicKey, OffsetInfo)]): Seq[ListOffsetsEntry] = {
    value.map {
      case (k, o) => ListOffsetsEntry(k, o)
    }.toSeq
  }
}

final case class OffsetInfo(offset: Long, timestamp: Long, leaderEpoch: Option[Long])

object OffsetInfo {
  def apply(value: ListOffsetsResultInfo): OffsetInfo = {
    OffsetInfo(
      value.offset(),
      value.timestamp(),
      Try(value.leaderEpoch().get.toLong).toOption
    )
  }

  implicit val codec = io.circe.generic.semiauto.deriveCodec[OffsetInfo]
}