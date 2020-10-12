package franz.rest.kafka.routes

import java.util.concurrent.TimeoutException

import cats.implicits._
import com.typesafe.config.{Config, ConfigFactory}
import franz.rest.EnvRuntime
import org.http4s.HttpRoutes
import zio.interop.catz._
import zio.{Has, Task, ZIO}

final case class KafkaApp(config: Config = ConfigFactory.load()) {

  def routes(implicit runtime: EnvRuntime): ZIO[ProducerOps, Throwable, HttpRoutes[Task]] = {

    val admin = AdminOps(config)

    val consumerRoutes =
      ConsumerRoutes.peekGet(r => onPeekRequest(r).provide(Has(admin))) <+>
        ConsumerRoutes.peekPost(r => onPeekRequest(r).provide(Has(admin)))

    val publishRoutesIO = ZIO.environment[ProducerOps].map { publish =>
      KafkaRoute.publish(publish.push) <+>
        KafkaRoute.publishBody(publish.push)
    }

    publishRoutesIO.map { publishRoutes =>
      adminRoutes(admin) <+>
        consumerRoutes <+>
        consumerGroupRoutes(admin) <+>
        publishRoutes
    }
  }

  private def consumerGroupRoutes(admin: AdminOps)(implicit runtime: EnvRuntime) = {
    ConsumerGroupRoutes.deleteGroup(admin.deleteGroup) <+>
      ConsumerGroupRoutes.consumerGroupStats(admin.consumerGroupStats) <+>
      ConsumerGroupRoutes.allConsumerGroupStats(admin.consumerGroupStats()) <+>
      ConsumerGroupRoutes.listConsumerGroups(admin.listConsumerGroups()) <+>
      ConsumerGroupRoutes.describeConsumerGroupsPost(describeConsumerGroups(admin)) <+>
      ConsumerGroupRoutes.describeConsumerGroupsGet(describeConsumerGroups(admin))
  }

  private def describeConsumerGroups(admin: AdminOps)(groupIds: Set[ConsumerGroupId], includeAuthorizedOperations: Boolean)(implicit runtime: EnvRuntime): Task[Map[ConsumerGroupId, ConsumerGroupDesc]] = {
    admin.describeConsumerGroups(groupIds, includeAuthorizedOperations)
      .timeout(admin.requestDurationJava)
      .provide(runtime.environment)
      .flatMap {
        case None => Task.fail(new TimeoutException(s"Request timed out after ${admin.requestTimeout}"))
        case Some(map) => Task(map)
      }
  }

  private def adminRoutes(admin: AdminOps)(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
    KafkaRoute.listTopics(admin.topics) <+>
      KafkaRoute.deleteTopics(admin.deleteTopics) <+>
      KafkaRoute.deleteTopic(admin.deleteTopics) <+>
      KafkaRoute.createTopic(admin.createTopic) <+>
      KafkaRoute.partitionsForTopicsGet(admin.partitionsForTopic) <+>
      KafkaRoute.partitionsForTopicsPost(admin.partitionsForTopic) <+>
      KafkaRoute.describeCluster(admin.describeCluster()) <+>
      KafkaRoute.metrics(admin.metrics()) <+>
      KafkaRoute.repartition(admin.createPartitions) <+>
      KafkaRoute.listOffsetsPost(admin.listOffsets) <+>
      KafkaRoute.listOffsetsGet(admin.listOffsetsForTopics) <+>
      KafkaRoute.listOffsetsAtTime(admin.listOffsetsForTopic)
  }
}