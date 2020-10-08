package franz.rest.kafka.routes

import io.circe.Decoder.Result
import io.circe.{Codec, HCursor, Json}
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo
import org.apache.kafka.clients.admin._
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common._
import org.apache.kafka.common.metrics.KafkaMetric

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
                           authorizedOperations: Set[String]) {
  def asTopicPartitions: Seq[TopicPartition] = partitions.map { p =>
    new TopicPartition(name, p.partition)
  }
}

object TopicDesc {
  def apply(value: TopicDescription): TopicDesc = {
    val authOps = Try(value.authorizedOperations().asScala.map(_.name()).toSet).getOrElse(Set.empty)

    new TopicDesc(value.name(), value.isInternal,
      value.partitions().asScala.map(TopicPartitionInfoDesc.apply).toSeq,
      authOps)
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

final case class CreatePartitionRequest(newPartitions: Map[String, UpdatedPartition], validateOnly: Boolean)

object CreatePartitionRequest {
  implicit val codec = io.circe.generic.semiauto.deriveCodec[CreatePartitionRequest]
}

case class ConsumerGroupMember(consumerId: String,
                               groupInstanceId: Option[String],
                               clientId: String,
                               host: String,
                               assignment: Set[TopicKey])

object ConsumerGroupMember {
  implicit val codec = io.circe.generic.semiauto.deriveCodec[ConsumerGroupMember]

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
      case Latest => OffsetSpec.latest()
      case Earliest => OffsetSpec.earliest()
      case Timestamp(at) => OffsetSpec.forTimestamp(at)
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

  implicit object codec extends Codec[Offset] {
    override def apply(c: HCursor): Result[Offset] = {
      c.as[String] match {
        case Right("latest") => Right(Latest)
        case Right("earliest") => Right(Earliest)
        case _ => c.as[Long].map(Timestamp.apply)
      }
    }

    override def apply(a: Offset): Json = a match {
      case Latest => Json.fromString("latest")
      case Earliest => Json.fromString("earliest")
      case Timestamp(ts) => Json.fromLong(ts)
    }
  }

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

final case class OffsetRange(topic: String, partition: Int, earliest: OffsetInfo, latest: OffsetInfo) {
  def asKey = TopicKey(topic, partition)
}

object OffsetRange {
  implicit val codec = io.circe.generic.semiauto.deriveCodec[OffsetRange]

  def apply(key: TopicKey, earliest: OffsetInfo, latest: OffsetInfo): OffsetRange = {
    OffsetRange(key.topic, key.partition, earliest, latest)
  }
}


object TopicOffsetsResponse {
  def apply(earliestResponse: Seq[ListOffsetsEntry],
            latestResponse: Seq[ListOffsetsEntry]): Seq[OffsetRange] = {
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
