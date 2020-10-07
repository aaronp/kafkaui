package franz.rest.kafka.routes

import franz.rest.{EnvRuntime, taskDsl}
import io.circe.Json
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityCodec._
import zio.Task
import zio.interop.catz._

object ConsumerGroupRoutes {

  import taskDsl._

  object IncludeAuthorizedOperationsOpt extends OptionalQueryParamDecoderMatcher[Boolean]("includeAuthorizedOperations")

  /**
   * GET /kafka/groups - returns a map of all consumer groups->topic->partition->offset/metadata
   */
  def listConsumerGroups(listGroups: Task[Json])(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case GET -> Root / "kafka" / "groups" => listGroups.flatMap { stats =>
        Ok(stats)
      }
    }
  }

  /**
   * DELETE /kafka/group
   * [...ids...]
   *
   * @param delete
   * @param runtime
   * @return
   */
  def deleteGroup(delete: Set[ConsumerGroupId] => Task[Set[ConsumerGroupId]])(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case req@DELETE -> Root / "kafka" / "group" =>
        for {
          groups <- req.as[Set[String]]
          result <- delete(groups)
          resp <- Ok(result)
        } yield resp
    }
  }

  def describeConsumerGroupsPost(onDescribe: (Set[Topic], Boolean) => Task[Map[ConsumerGroupId, ConsumerGroupDesc]])(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case req@POST -> Root / "kafka" / "group" / "describe" :? IncludeAuthorizedOperationsOpt(includeAuth) =>
        for {
          topics <- req.as[Set[Topic]]
          map <- onDescribe(topics, includeAuth.getOrElse(false))
          result <- Ok(map)
        } yield result
    }
  }

  def describeConsumerGroupsGet(onDescribe: (Set[Topic], Boolean) => Task[Map[ConsumerGroupId, ConsumerGroupDesc]])(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case GET -> Root / "kafka" / "group" / "describe" / commaSeparatedTopics :? IncludeAuthorizedOperationsOpt(includeAuth) =>
        val topics = commaSeparatedTopics.split(",", -1).toSet
        for {
          map <- onDescribe(topics, includeAuth.getOrElse(false))
          result <- Ok(map)
        } yield result
    }
  }

  /**
   * GET /kafka/groups/stats - returns a map of all consumer groups->topic->partition->offset/metadata
   */
  def allConsumerGroupStats(listStats: Task[Json])(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case GET -> Root / "kafka" / "groups" / "stats" => listStats.flatMap { stats =>
        Ok(stats)
      }
    }
  }

  /**
   * GET /kafka/groups - returns a map of all consumer groups->topic->partition->offset/metadata
   */
  def consumerGroupStats(listStats: String => Task[Json])(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case GET -> Root / "kafka" / "group" / "stats" / consumerGroup => listStats(consumerGroup).flatMap { stats =>
        Ok(stats)
      }
    }
  }
}
