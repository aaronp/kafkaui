package franz.rest.kafka.routes

import java.util
import java.util.Properties
import java.util.concurrent.TimeUnit

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import franz.rest.kafka.routes.AdminOps.CreateTopic
import io.circe.Json
import io.circe.syntax._
import kafka4m.admin.{ConsumerGroupStats, RichKafkaAdmin}
import kafka4m.util.Props
import org.apache.kafka.clients.admin._
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.KafkaFuture.BiConsumer
import org.apache.kafka.common.acl.AclOperation
import org.apache.kafka.common.metrics.KafkaMetric
import org.apache.kafka.common.{KafkaFuture, MetricName, Node, TopicPartition}
import zio.blocking.Blocking
import zio.{Task, UIO, ZEnv, ZIO}

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._
import scala.util.Try

final case class AdminOps(admin: RichKafkaAdmin, requestTimeout: FiniteDuration)(implicit rt: zio.Runtime[ZEnv]) extends AutoCloseable with StrictLogging {

  def requestDurationJava = java.time.Duration.ofMillis(requestTimeout.toMillis)

  override def close(): Unit = admin.close()

  val closeTask: UIO[Unit] = Task(close()).fork.ignore

  private implicit def richFuture[A](future: KafkaFuture[A]) = new {
    def asTask = {
      val blocked: ZIO[Blocking, Throwable, A] = zio.blocking.blocking {
        //        ZIO.fromFuture(_ => Future(future.get(requestTimeout.toMillis, TimeUnit.MILLISECONDS)))
        Task.effectAsync[A] { cb =>
          future.whenComplete(new BiConsumer[A, Throwable] {
            override def accept(result: A, err: Throwable): Unit = {
              println(s"task complete w/ $result or $err")
              if (result == null) {
                cb(Task.fail(err))
              } else {
                cb(Task.succeed(result))
              }
            }
          })
        }
      }
      blocked.provide(rt.environment)
    }
  }

  def createPartitions(request: CreatePartitionRequest): Task[Set[Topic]] = {
    val newPartitions = request.newPartitions.view.mapValues(_.asNewPartitions).toMap.asJava
    for {
      createResult: CreatePartitionsResult <- Task(admin.admin.createPartitions(newPartitions, (new CreatePartitionsOptions).validateOnly(request.validateOnly)))
      _ <- createResult.all().asTask
    } yield createResult.values.keySet().asScala.toSet
  }

  def updatePartitions(request: AlterPartitionRequest): ZIO[Any, Throwable, Unit] = {
    for {
      createResult <- Task(admin.admin.alterPartitionReassignments(request.asJava))
      _ <- createResult.all().asTask
    } yield ()
  }

  def deleteGroup(consumerGroups: Set[ConsumerGroupId]): Task[Set[ConsumerGroupId]] = {
    for {
      r <- Task(admin.admin.deleteConsumerGroups(consumerGroups.asJava))
      //      _ <- r.all().asTask.unit
    } yield r.deletedGroups().asScala.keySet.toSet
  }

  def metrics(): ZIO[Any, Throwable, Map[String, List[MetricEntry]]] = {
    for {
      metrics <- Task(admin.admin.metrics())
      all = metrics.asScala.collect {
        case (name: MetricName, value: KafkaMetric) =>
          MetricEntry(MetricKey(name), Metric(value))
      }
    } yield {
      all
        .groupBy(_.metric.group)
        .view
        .mapValues(_.toList)
        .toMap
    }
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

  def listOffsetsForTopic(topic: Topic, offset: Offset): ZIO[Any, Throwable, Seq[ListOffsetsEntry]] = {
    def asRequest(topicDesc: Map[Topic, TopicDesc]): ListOffsetsRequest = {
      val positions = for {
        (topic, desc) <- topicDesc
        partition <- desc.partitions
      } yield ReadPositionAt(TopicKey(topic, partition.partition), offset)
      ListOffsetsRequest(positions.toList, false)
    }

    for {
      topicDesc <- partitionsForTopic(Set(topic))
      entries <- listOffsets(asRequest(topicDesc))
    } yield {
      entries
    }
  }

  def listOffsets(request: ListOffsetsRequest): Task[Seq[ListOffsetsEntry]] = {
    for {
      result: ListOffsetsResult <- Task(admin.admin.listOffsets(request.asJavaMap, request.options))
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
        Try(authorizedOperations.asScala.map(_.name()).toSet).getOrElse(Set.empty)
      )
    }
  }

  def describeConsumerGroups(groupIds: Set[ConsumerGroupId], includeAuthorizedOperations: Boolean): Task[Map[ConsumerGroupId, ConsumerGroupDesc]] = {
    for {
      result <- Task(admin.admin.describeConsumerGroups(groupIds.asJava, (new DescribeConsumerGroupsOptions).includeAuthorizedOperations(includeAuthorizedOperations)))
      byTopicMap <- ZIO.foreachPar(result.describedGroups().asScala.toList) {
        case (key, future) =>
          future.asTask.map { result =>
            (key, ConsumerGroupDesc(result))
          }
      }
    } yield byTopicMap.toMap
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

  def deleteTopics(topics: Set[String]): Task[Set[ConsumerGroupId]] = {
    def block(result: Either[Throwable, DeleteTopicsResult]) = {
      Task.fromEither(result).flatMap(_.all().asTask).either
    }

    for {
      result <- Task(admin.admin.deleteTopics(topics.asJava)).either
      _ <- block(result)
    } yield {
      result match {
        case Left(_) => Set.empty
        case Right(ids) => ids.values().keySet().asScala.toSet
      }
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

  def apply(rootConfig: Config = ConfigFactory.load())(implicit rt: zio.Runtime[ZEnv]): AdminOps = {

    import args4c.implicits._
    val kafkaCfg = rootConfig.getConfig("franz.kafka.admin")
    val requestTimeout = rootConfig.asFiniteDuration("franz.kafka.requestTimeout")

    val props: Properties = Props.propertiesForConfig(kafkaCfg)
    val admin: AdminClient = AdminClient.create(props)
    AdminOps(new RichKafkaAdmin(admin), requestTimeout)
  }
}
