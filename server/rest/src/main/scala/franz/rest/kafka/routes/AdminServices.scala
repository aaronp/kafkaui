package franz.rest.kafka.routes

import java.util.Properties
import java.util.concurrent.TimeUnit

import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import franz.rest.kafka.routes.AdminServices.CreateTopic
import io.circe.Json
import io.circe.syntax._
import kafka4m.admin.{ConsumerGroupStats, RichKafkaAdmin}
import kafka4m.util.Props
import org.apache.kafka.clients.admin.{AdminClient, ListTopicsOptions}
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import zio.{Task, ZIO}

import scala.concurrent.duration.FiniteDuration

case class AdminServices(admin: RichKafkaAdmin, requestTimeout: FiniteDuration) extends StrictLogging {


  def topics(listInternal: Boolean): Task[Map[String, Boolean]] = {
    Task.fromFuture { implicit ec =>
      admin.topics(new ListTopicsOptions().listInternal(listInternal))
    }.map { byName =>
      byName
        .view
        .mapValues(_.isInternal)
        .toMap
    }
  }

  type ConsumerGroupId = String
  type Topic = String
  type Partition = Int

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

  def consumerGroupStats(forGroup: String): Task[Json] = {
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

object AdminServices {

  case class CreateTopic(name: String, numPartitions: Int = 1, replicationFactor: Short = 1)

  object CreateTopic {
    implicit val codec = io.circe.generic.semiauto.deriveCodec[CreateTopic]
  }

  def apply(rootConfig: Config): AdminServices = {

    import args4c.implicits._
    val kafkaCfg = rootConfig.getConfig("franz.kafka.admin")
    val requestTimeout = rootConfig.asFiniteDuration("franz.kafka.requestTimeout")

    val props: Properties = Props.propertiesForConfig(kafkaCfg)
    val admin: AdminClient = AdminClient.create(props)
    AdminServices(new RichKafkaAdmin(admin), requestTimeout)
  }
}
