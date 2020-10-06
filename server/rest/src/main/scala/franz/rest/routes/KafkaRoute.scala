package franz.rest.routes

import franz.rest.KafkaService.CreateTopic
import io.circe.Json
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityCodec._
import zio.Task
import zio.interop.catz._

object KafkaRoute {

  import taskDsl._

  object ListInternal extends OptionalQueryParamDecoderMatcher[Boolean]("internal")

  /**
   * GET /kafka/topics?internal=rue
   */
  def listTopics(listTopicsTask: Boolean => Task[Map[String, Boolean]])(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case GET -> Root / "kafka" / "topics" :? ListInternal(listInternal) =>
        listTopicsTask(listInternal.getOrElse(false)).flatMap { topics =>
          Ok(topics)
        }
    }
  }

  /**
   * GET /kafka/groups - returns a map of all consumer groups->topic->partition->offset/metadata
   */
  def listConsumerGroups(listGroups : Task[Json])(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case GET -> Root / "kafka" / "groups" =>
        listGroups.flatMap { stats =>
          Ok(stats)
        }
    }
  }

  /**
   * GET /kafka/groups/stats - returns a map of all consumer groups->topic->partition->offset/metadata
   */
  def allConsumerGroupStats(listStats: Task[Json])(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case GET -> Root / "kafka" / "groups" / "stats" =>
        listStats.flatMap { stats =>
          Ok(stats)
        }
    }
  }

  /**
   * GET /kafka/groups - returns a map of all consumer groups->topic->partition->offset/metadata
   */
  def consumerGroupStats(listStats: String => Task[Json])(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case GET -> Root / "kafka" / "group" / consumerGroup =>
        listStats(consumerGroup).flatMap { stats =>
          Ok(stats)
        }
    }
  }

  /**
   * POST /kafka/topic
   */
  def createTopic(createTopic: CreateTopic => Task[CreateTopic])(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case req@POST -> Root / "kafka" / "topic" =>
        for {
          request <- req.as[CreateTopic]
          result <- createTopic(request)
          resp <- Ok(result)
        } yield resp
    }
  }
}
