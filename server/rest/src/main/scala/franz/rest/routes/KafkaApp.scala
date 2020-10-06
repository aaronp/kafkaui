package franz.rest.routes

import cats.implicits._
import com.typesafe.config.Config
import franz.rest.KafkaService
import org.http4s.HttpRoutes
import zio.Task
import zio.interop.catz._

case class KafkaApp(config: Config) {

  val app = KafkaService(config)

  def routes(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
    KafkaRoute.listTopics(internal => app.topics(internal)) <+>
      KafkaRoute.createTopic(app.createTopic) <+>
      KafkaRoute.allConsumerGroupStats(app.consumerGroupStats) <+>
      KafkaRoute.consumerGroupStats(app.consumerGroupStats) <+>
      KafkaRoute.listConsumerGroups(app.listConsumerGroups)
  }
}
