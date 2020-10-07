package franz.rest.kafka.routes

import cats.implicits._
import com.typesafe.config.Config
import franz.rest.EnvRuntime
import org.http4s.HttpRoutes
import zio.interop.catz._
import zio.{Task, ZIO}

case class KafkaApp(config: Config) {

  def routes(implicit runtime: EnvRuntime): ZIO[ProducerOps, Throwable, HttpRoutes[Task]] = {

    val admin = AdminOps(config)
    val adminRoutes: HttpRoutes[Task] = KafkaRoute.listTopics(internal => admin.topics(internal)) <+>
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
      ConsumerGroupRoutes.describeConsumerGroupsPost(admin.describeConsumerGroups) <+>
      ConsumerGroupRoutes.describeConsumerGroupsGet(admin.describeConsumerGroups)

    val publishRoutes = ZIO.environment[ProducerOps].map { publish =>
      KafkaRoute.publish(publish.push) <+>
        KafkaRoute.publishBody(publish.push)
    }

    publishRoutes.map { route =>
      adminRoutes <+> route
    }
  }
}