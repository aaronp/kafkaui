package franz.rest.kafka.routes

import java.util.concurrent.TimeoutException

import cats.implicits._
import com.typesafe.config.Config
import franz.rest.EnvRuntime
import org.http4s.HttpRoutes
import zio.interop.catz._
import zio.{IO, Task, ZIO}

case class KafkaApp(config: Config) {

  def routes(implicit runtime: EnvRuntime): ZIO[ProducerOps, Throwable, HttpRoutes[Task]] = {

    val admin = AdminOps(config)

    def describeConsumerGroups(groupIds: Set[ConsumerGroupId], includeAuthorizedOperations: Boolean): Task[Map[ConsumerGroupId, ConsumerGroupDesc]] = {
      admin.describeConsumerGroups(groupIds, includeAuthorizedOperations)
        .timeout(admin.requestDurationJava)
        .provide(runtime.environment)
        .flatMap {
          case None => Task.fail(new TimeoutException(s"Request timed out after ${admin.requestTimeout}"))
          case Some(map) => Task(map)
        }
    }

    val adminRoutes: HttpRoutes[Task] = KafkaRoute.listTopics(admin.topics) <+>
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
      ConsumerGroupRoutes.deleteGroup(admin.deleteGroup) <+>
      ConsumerGroupRoutes.consumerGroupStats(admin.consumerGroupStats) <+>
      ConsumerGroupRoutes.allConsumerGroupStats(admin.consumerGroupStats()) <+>
      ConsumerGroupRoutes.listConsumerGroups(admin.listConsumerGroups()) <+>
      ConsumerGroupRoutes.describeConsumerGroupsPost(describeConsumerGroups) <+>
      ConsumerGroupRoutes.describeConsumerGroupsGet(describeConsumerGroups)

    val publishRoutes = ZIO.environment[ProducerOps].map { publish =>
      KafkaRoute.publish(publish.push) <+>
        KafkaRoute.publishBody(publish.push)
    }

    publishRoutes.map { route =>
      adminRoutes <+> route
    }
  }
}