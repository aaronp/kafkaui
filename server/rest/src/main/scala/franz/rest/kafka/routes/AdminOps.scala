package franz.rest.kafka.routes

import java.util
import java.util.Properties
import java.util.concurrent.TimeUnit

import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import franz.rest.kafka.routes.AdminOps.CreateTopic
import io.circe.Json
import io.circe.syntax._
import kafka4m.admin.{ConsumerGroupStats, RichKafkaAdmin}
import kafka4m.util.Props
import org.apache.kafka.clients.admin._
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.acl.AclOperation
import org.apache.kafka.common.metrics.KafkaMetric
import org.apache.kafka.common.{KafkaFuture, MetricName, Node, TopicPartition}
import zio.{Task, ZIO}

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

case class AdminOps(admin: RichKafkaAdmin, requestTimeout: FiniteDuration) extends StrictLogging {

  private implicit def richFuture[A](future: KafkaFuture[A]) = new {
    def asTask = {
      Task[A](future.get(requestTimeout.toMillis, TimeUnit.MILLISECONDS))
    }
  }

  def createPartitions(request: CreatePartitionRequest): Task[Set[Topic]] = {
    val newPartitions = request.newPartitions.view.mapValues(_.asNewPartitions).toMap.asJava
    for {
      createResult: CreatePartitionsResult <- Task(admin.admin.createPartitions(newPartitions, (new CreatePartitionsOptions).validateOnly(request.validateOnly)))
      _ <- createResult.all().asTask
    } yield createResult.values.keySet().asScala.toSet
  }

  def metrics(): Task[List[(MetricKey, String)]] = {
    for {
      metrics <- Task(admin.admin.metrics())
      all = metrics.asScala.collect {
        case (name: MetricName, value: KafkaMetric) =>
          MetricKey(name) -> Metric(value)
      }
    } yield all.toList
  }

  def listOffsetsForTopics(topics: Set[Topic]): Task[Seq[OffsetRange]] = {
    def asRequest(topicDesc: Map[Topic, TopicDesc], offset: Offset): ListOffsetsRequest = {
      val positions = for {
        (topic, desc) <- topicDesc
        partition <- desc.partitions
      } yield ReadPositionAt(TopicKey(topic, partition.partition), offset)
      ListOffsetsRequest(positions.toList, false)
    }

    for {
      topicDesc <- partitionsForTopic(topics)
      earliestResponse: Seq[ListOffsetsEntry] <- listOffsets(asRequest(topicDesc, Offset.earliest))
      latestResponse: Seq[ListOffsetsEntry] <- listOffsets(asRequest(topicDesc, Offset.latest))
    } yield {
      TopicOffsetsResponse(earliestResponse, latestResponse)
    }
  }

  def listOffsets(request: ListOffsetsRequest): Task[Seq[ListOffsetsEntry]] = {
    for {
      result <- Task(admin.admin.listOffsets(request.asJavaMap, request.options))
      responseMap <- result.all().asTask
    } yield {
      ListOffsetsEntry {
        responseMap.asScala.map {
          case (key, value) => TopicKey(key) -> OffsetInfo(value)
        }
      }
    }
  }

  def describeCluster(): Task[DescribeCluster] = {
    for {
      result <- Task(admin.admin.describeCluster())
      nodes: util.Collection[Node] <- result.nodes().asTask
      controller: Node <- result.controller().asTask
      clusterId <- result.clusterId().asTask
      authorizedOperations: util.Set[AclOperation] <- result.authorizedOperations().asTask
    } yield {
      DescribeCluster(
        nodes.asScala.map(NodeDesc.apply).toSeq,
        NodeDesc(controller),
        clusterId,
        authorizedOperations.asScala.map(_.name()).toSet
      )
    }
  }

  def describeConsumerGroups(groupIds: Set[Topic], includeAuthorizedOperations: Boolean): Task[Map[ConsumerGroupId, ConsumerGroupDesc]] = {
    for {
      result <- Task(admin.admin.describeConsumerGroups(groupIds.asJava, (new DescribeConsumerGroupsOptions).includeAuthorizedOperations(includeAuthorizedOperations)))
      byTopic <- result.all().asTask
    } yield {
      byTopic.asScala.view.mapValues(ConsumerGroupDesc.apply).toMap
    }
  }

  def topics(listInternal: Boolean): Task[Map[Topic, Boolean]] = {
    Task.fromFuture { implicit ec =>
      admin.topics(new ListTopicsOptions().listInternal(listInternal))
    }.map { byName =>
      byName
        .view
        .mapValues(_.isInternal)
        .toMap
    }
  }

  def partitionsForTopic(topics: Set[Topic]): Task[Map[Topic, TopicDesc]] = {
    Task {
      val result = admin.admin.describeTopics(topics.asJava)
      val resultsByTopic: mutable.Map[Topic, TopicDescription] = result.all().get(requestTimeout.toMillis, TimeUnit.MILLISECONDS).asScala
      resultsByTopic.view.mapValues(TopicDesc.apply).toMap
    }
  }

  def consumerGroupStats(): Task[Json] = {
    Task.fromFuture(implicit ec => admin.consumerGroupsStats).map { stats: Seq[ConsumerGroupStats] =>
      val offsetsByTopicByGroup: Map[ConsumerGroupId, Map[Topic, Map[Partition, OffsetMetadata]]] = stats.foldLeft(Map[ConsumerGroupId, Map[Topic, Map[Partition, OffsetMetadata]]]()) {
        case (byGroupId, next) =>
          require(!byGroupId.contains(next.groupId))
          val byTopicMap = asTopicMap(next.offsetsByTopicPartition)
          byGroupId.updated(next.groupId, byTopicMap)
      }
      offsetsByTopicByGroup.asJson
    }
  }

  def listConsumerGroups(): ZIO[Any, Throwable, Json] = {
    Task.fromFuture(implicit ec => admin.consumerGroups).map { all =>
      all.map(ConsumerGroupEntry.apply).asJson
    }
  }

  def consumerGroupStats(forGroup: ConsumerGroupId): Task[Json] = {
    Task.fromFuture(implicit ec => admin.consumerGroupsPositions(forGroup)).map { stats =>
      asTopicMap(stats).asJson
    }
  }

  def createTopic(request: CreateTopic): ZIO[Any, Throwable, CreateTopic] = {
    for {
      resp <- Task(admin.createTopic(request.name, request.numPartitions, request.replicationFactor))
      numPartitions <- Task(resp.numPartitions(request.name).get(requestTimeout.toMillis, TimeUnit.MILLISECONDS))
      replicationFactor <- Task(resp.replicationFactor(request.name).get(requestTimeout.toMillis, TimeUnit.MILLISECONDS))
    } yield {
      CreateTopic(request.name, numPartitions.intValue(), replicationFactor.shortValue())
    }
  }

  private def asTopicMap(offsetsByTopicPartition: Map[TopicPartition, OffsetAndMetadata]): Map[Topic, Map[Partition, OffsetMetadata]] = {
    val byTopicMap: Map[Topic, Map[Partition, OffsetMetadata]] = offsetsByTopicPartition.foldLeft(Map[Topic, Map[Partition, OffsetMetadata]]()) {
      case (byTopic, (key, metad8a)) =>
        val byPartition = byTopic.getOrElse(key.topic(), Map.empty)
        val newMap = byPartition.updated(key.partition(), OffsetMetadata(metad8a))
        byTopic.updated(key.topic(), newMap)
    }
    byTopicMap
  }

}

object AdminOps {

  case class CreateTopic(name: Topic, numPartitions: Int = 1, replicationFactor: Short = 1)

  object CreateTopic {
    implicit val codec = io.circe.generic.semiauto.deriveCodec[CreateTopic]
  }

  def apply(rootConfig: Config): AdminOps = {

    import args4c.implicits._
    val kafkaCfg = rootConfig.getConfig("franz.kafka.admin")
    val requestTimeout = rootConfig.asFiniteDuration("franz.kafka.requestTimeout")

    val props: Properties = Props.propertiesForConfig(kafkaCfg)
    val admin: AdminClient = AdminClient.create(props)
    AdminOps(new RichKafkaAdmin(admin), requestTimeout)
  }
}
