package franz.rest.kafka.routes

import java.util.Base64

import franz.rest.kafka.routes.AdminOps.CreateTopic
import franz.rest.{EnvRuntime, taskDsl}
import io.circe.Json
import org.http4s.circe.CirceEntityCodec._
import org.http4s.{DecodeResult, EntityDecoder, HttpRoutes}
import zio.Task
import zio.interop.catz._

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

  def listOffsetsPost(list: ListOffsetsRequest => Task[Seq[ListOffsetsEntry]])(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case req@POST -> Root / "kafka" / "offsets" =>
        for {
          request <- req.as[ListOffsetsRequest]
          result <- list(request)
          resp <- Ok(result)
        } yield resp
    }
  }

  def listOffsetsGet(list: Set[Topic] => Task[Seq[OffsetRange]])(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case GET -> Root / "kafka" / "offsets" / topics =>
        val topicSet = topics.split(",", -1).toSet
        for {
          result <- list(topicSet)
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
   * POST /kafka/topics
   */
  def deleteTopics(delete: Set[String] => Task[Set[String]])(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case req@POST -> Root / "kafka" / "topics" / "delete" => for {
        topics <- req.as[Set[String]]
        done <- delete(topics)
        result <- Ok(done)
      } yield result
    }
  }

  /**
   * DELETE /kafka/topic/<topic>
   */
  def deleteTopic(delete: Set[String] => Task[Set[String]])(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case req@DELETE -> Root / "kafka" / "topic" / topic => for {
        done <- delete(Set(topic))
        result <- Ok(done)
      } yield result
    }
  }

  /**
   * GET /kafka/cluster
   */
  def describeCluster(describe: Task[DescribeCluster])(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case GET -> Root / "kafka" / "cluster" => describe.flatMap { result =>
        Ok(result)
      }
    }
  }


  /**
   * GET /kafka/metrics
   */
  def metrics(getMetrics: Task[List[(MetricKey, String)]])(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case GET -> Root / "kafka" / "metrics" => getMetrics.flatMap { result =>
        Ok(result)
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
   * GET /kafka/partitions/<topic-list>
   */
  def partitionsForTopicsGet(partitionsForTopic: Set[String] => Task[Map[String, TopicDesc]])(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case GET -> Root / "kafka" / "partitions" / topics =>
        val topicSet: Set[Topic] = topics.split(",", -1).map(_.trim).toSet
        partitionsForTopic(topicSet).flatMap { topics =>
          Ok(topics)
        }
    }
  }


  /**
   * POST /kafka/repartition
   */
  def repartition(onRequest: CreatePartitionRequest => Task[Set[String]])(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case httpReq@POST -> Root / "kafka" / "repartition" =>
        for {
          request <- httpReq.as[CreatePartitionRequest]
          result <- onRequest(request)
          resp <- Ok(result)
        } yield resp
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
