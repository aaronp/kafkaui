package franz.rest.kafka.routes

import java.time.{ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter
import java.util.{Base64, Optional}

import franz.rest.kafka.routes.MetricEntry.FmtSuffixes
import io.circe.Decoder.Result
import io.circe.{Codec, HCursor, Json}
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo
import org.apache.kafka.clients.admin._
import org.apache.kafka.clients.consumer.{ConsumerRecord, OffsetAndMetadata}
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

final case class PublishOne(topic: String, key: String, value: String, partition: Option[Int] = None, isBase64: Boolean = false)

object PublishOne {
  implicit val codec = io.circe.generic.semiauto.deriveCodec[PublishOne]

  def apply(topic: String, key: String, value: Array[Byte], partition: Option[Int]): PublishOne = {
    new PublishOne(topic, key, Base64.getEncoder.encodeToString(value), partition, isBase64 = true)
  }
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

  def empty = NodeDesc(-1, "", "", -1, None)

  implicit val codec = io.circe.generic.semiauto.deriveCodec[NodeDesc]
}

final case class TopicPartitionInfoDesc(partition: Int, leader: NodeDesc, replicas: Seq[NodeDesc], isr: Seq[NodeDesc])

object TopicPartitionInfoDesc {
  def apply(value: TopicPartitionInfo): TopicPartitionInfoDesc = {
    new TopicPartitionInfoDesc(value.partition(),
      Option(value.leader()).map(NodeDesc.apply).getOrElse(NodeDesc.empty),
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

  def human(key: String) = {
    key.split("-", -1).map {
      case name if name.equalsIgnoreCase("io") => "IO"
      case name if name.equalsIgnoreCase("iotime") => "IO Time"
      case name if name.equalsIgnoreCase("ms") => "ms"
      case name if name.equalsIgnoreCase("waittime") => "Wait Time"
      case name => name.capitalize
    }.mkString(" ") match {
      case key if key.equalsIgnoreCase("start time ms") => "Start Time"
      case key => key
    }
  }

  def apply(value: MetricName): MetricKey = {
    val label = human(value.name())
    new MetricKey(
      label,
      human(value.group()),
      value.description(),
      value.tags().asScala.toMap
    )
  }
}

final case class MetricEntry(metric: MetricKey, value: String) {
  def fmtAsTime = {
    val instant = java.time.Instant.ofEpochMilli(value.toLong)
    val time = ZonedDateTime.ofInstant(instant, ZoneId.of("UTC"))

    DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(time)
  }

  def formattedValue: String = metric.name.toLowerCase match {
    case key if FmtSuffixes.exists(key.endsWith) =>
      Try(f"${value.toDouble}%1.4f").getOrElse(value)
    case key if key.startsWith("start time") =>
      Try(fmtAsTime).getOrElse(value)
    case _ => value
  }
}

object MetricEntry {

  val FmtSuffixes = Set("rate", "ratio", "avg")
  private val autoCodec = io.circe.generic.semiauto.deriveCodec[MetricEntry]

  implicit object codec extends Codec[MetricEntry] {

    import io.circe.syntax._

    override def apply(a: MetricEntry): Json = {
      Json.obj("metric" -> a.metric.asJson,
        "value" -> Json.fromString(a.formattedValue)
      )
    }

    override def apply(c: HCursor): Result[MetricEntry] = autoCodec(c)
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

final case class AlterPartitionRequest(targetReplicasByPartition: Seq[(TopicKey, Option[List[Int]])]) {
  def asJava = {
    val jMap = new java.util.HashMap[TopicPartition, Optional[NewPartitionReassignment]]()
    targetReplicasByPartition.foreach {
      case (key, Some(targetReplicas)) =>
        val replicas = Optional.of(new NewPartitionReassignment(targetReplicas.map(Integer.valueOf).asJava))
        jMap.put(key.asJava, replicas)
      case (key, None) => jMap.put(key.asJava, Optional.empty())
    }
    jMap
  }
}

object AlterPartitionRequest {
  def of(entries: (TopicKey, List[Int])*): AlterPartitionRequest = {
    val map: Seq[(TopicKey, Option[List[Partition]])] = entries.map {
      case (k, Nil) => (k, None)
      case (k, list) => (k, Some(list))
    }
    new AlterPartitionRequest(map)
  }

  implicit val codec = io.circe.generic.semiauto.deriveCodec[AlterPartitionRequest]
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

  def at(time: Long) = Timestamp(time)

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

/**
 *
 * @param topics
 * @param fromOffset
 * @param limit
 * @param partitions empty partitions means all partitions
 */
final case class PeekRequest(topics: Set[String], fromOffset: Long, limit: Long, partitions: Set[Int] = Set.empty)

object PeekRequest {
  implicit val codec = io.circe.generic.semiauto.deriveCodec[PeekRequest]

  def apply(topic :String, fromOffset: Long, limit: Long, partitions: Set[Int]): PeekRequest = {
    new PeekRequest(Set(topic), fromOffset, limit, partitions)
  }
}

case class Record(topic: String,
                  key : String,
                  offset: Long,
                  leaderEpoch: Option[Int],
                  partition: Int,
                  serializedKeySize: Int,
                  serializedValueSize: Int,
                  timestamp: Long,
                  timestampType: String,
                  value: String,
                  base64: String)

object Record {
  def apply(value: ConsumerRecord[String, Array[Byte]]): Record = {
    Record(
      topic = value.topic,
      key = value.key(),
      offset = value.offset,
      leaderEpoch = Try(value.leaderEpoch.get().intValue()).toOption,
      partition = value.partition,
      serializedKeySize = value.serializedKeySize,
      serializedValueSize = value.serializedValueSize,
      timestamp = value.timestamp,
      timestampType = value.timestampType.name,
      value = new String(value.value),
      base64 = Base64.getEncoder.encodeToString(value.value),
    )
  }

  implicit val codec = io.circe.generic.semiauto.deriveCodec[Record]
}

final case class PeekResponse(records: Seq[Record])

object PeekResponse {
  implicit val codec = io.circe.generic.semiauto.deriveCodec[PeekResponse]
}