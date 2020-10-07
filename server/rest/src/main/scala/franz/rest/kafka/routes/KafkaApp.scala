package franz.rest.kafka.routes

import cats.implicits._
import com.typesafe.config.Config
import franz.rest.EnvRuntime
import org.http4s.HttpRoutes
import zio.interop.catz._
import zio.{Task, ZIO}

case class KafkaApp(config: Config) {


  def routes(implicit runtime: EnvRuntime): ZIO[ProducerServices, Throwable, HttpRoutes[Task]] = {

    val admin = AdminServices(config)
    val adminRoutes: HttpRoutes[Task] = KafkaRoute.listTopics(internal => admin.topics(internal)) <+>
      KafkaRoute.createTopic(admin.createTopic) <+>
      KafkaRoute.allConsumerGroupStats(admin.consumerGroupStats) <+>
      KafkaRoute.consumerGroupStats(admin.consumerGroupStats) <+>
      KafkaRoute.listConsumerGroups(admin.listConsumerGroups) <+>
      KafkaRoute.paritionsForTopicsGet(admin.partitionsForTopic) <+>
      KafkaRoute.partitionsForTopicsPost(admin.partitionsForTopic)

    val publishRoutes = ZIO.environment[ProducerServices].map { publish =>
      KafkaRoute.publish(publish.push) <+>
        KafkaRoute.publishBody(publish.push)
    }

    publishRoutes.map { route =>
      adminRoutes <+> route
    }
  }

}
