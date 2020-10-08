package franz.rest.kafka.routes

import franz.rest.{EnvRuntime, taskDsl}
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityCodec._
import zio.Task
import zio.interop.catz._

object ConsumerRoutes {

  import taskDsl._

  object PartitionsMatcher extends OptionalQueryParamDecoderMatcher[String]("partitions")

  object FromOffset extends OptionalQueryParamDecoderMatcher[Long]("from")

  object Limit extends OptionalQueryParamDecoderMatcher[Long]("limit")

  def peekPost(peek: PeekRequest => Task[PeekResponse])(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case req@POST -> Root / "kafka" / "consumer" / "peek" =>
        for {
          request <- req.as[PeekRequest]
          results <- peek(request)
          result <- Ok(results)
        } yield result
    }
  }
  private def parseInts(str : String): Set[Partition] = {
    str.split(",", -1).map(_.toInt).toSet
  }

  def peekGet(peek: PeekRequest => Task[PeekResponse])(implicit runtime: EnvRuntime): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case GET -> Root / "kafka" / "consumer" / "peek" / topics :? PartitionsMatcher(partitions) +& FromOffset(from) +& Limit(limit) =>
        val partitionFilter: Set[Int] = partitions.map(parseInts).getOrElse(Set.empty)

        val request = PeekRequest(
          topics.split(",", -1).toSet,
          from.getOrElse(0),
          limit.getOrElse(100),
          partitionFilter)
        for {
          results <- peek(request)
          result <- Ok(results)
        } yield result
    }
  }
}
