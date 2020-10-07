package franz.rest.kafka.routes

import java.util.Base64

import franz.rest.kafka.routes.AdminServices.CreateTopic
import franz.rest.{EnvRuntime, taskDsl}
import io.circe.Json
import org.http4s.{DecodeResult, EntityDecoder, HttpRoutes}
import org.http4s.circe.CirceEntityCodec._
import zio.Task
import zio.interop.catz._
import cats.implicits._

object KafkaRoute {

  import taskDsl._

  object ListInternal extends OptionalQueryParamDecoderMatcher[Boolean]("internal")

  object PartitionOpt extends OptionalQueryParamDecoderMatcher[Int]("partition")

  def publish(publish: PublishOne => Task[RecordMetadataResponse])(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case req@POST -> Root / "kafka" / "publish" =>
        for {
          request <- req.as[PublishOne]
          result <- publish(request)
          resp <- Ok(result)
        } yield resp
    }
  }

  def publishBody(publish: PublishOne => Task[RecordMetadataResponse])(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case req@POST -> Root / "kafka" / "publish" / topic / key :? PartitionOpt(partitionOpt) =>
        val bad: EntityDecoder[Task, Array[Byte]] = EntityDecoder.byteArrayDecoder[Task]
        val decoded: DecodeResult[Task, Array[Byte]] = bad.decode(req, true)
        val bytesT: Task[Array[Byte]] = decoded.rethrowT
        for {
          bytes <- bytesT
          base64 = Base64.getEncoder.encodeToString(bytes)
          request = PublishOne(topic, key, base64, partitionOpt, isBase64 = true)
          result <- publish(request)
          resp <- Ok(result)
        } yield resp
    }
  }

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
   * GET /kafka/partitions/<topic-list>
   */
  def paritionsForTopicsGet(partitionsForTopic: Set[String] => Task[Map[String, TopicDesc]])(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case GET -> Root / "kafka" / "partitions" / topics =>
        val topicSet = topics.split(",", -1).toSet.map(_.trim)
        partitionsForTopic(topicSet).flatMap { topics =>
          Ok(topics)
        }
    }
  }

  /**
   * POST kafka/partitions
   * [a,b,c]
   */
  def partitionsForTopicsPost(partitionsForTopic: Set[String] => Task[Map[String, TopicDesc]])(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case req@POST -> Root / "kafka" / "partitions" =>
        for {
          topics <- req.as[Set[String]]
          result <- partitionsForTopic(topics)
          resp <- Ok(result)
        } yield resp
    }
  }

  /**
   * GET /kafka/groups - returns a map of all consumer groups->topic->partition->offset/metadata
   */
  def listConsumerGroups(listGroups: Task[Json])(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
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
