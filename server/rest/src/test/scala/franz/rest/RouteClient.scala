package franz.rest

import franz.rest.kafka.routes.AdminOps.CreateTopic
import franz.rest.kafka.routes.{ConsumerGroupDesc, ConsumerGroupId, ListOffsetsEntry, ListOffsetsRequest, MetricEntry, OffsetRange, PeekRequest, PeekResponse, PublishOne, RecordMetadataResponse, Topic, TopicDesc}
import io.circe.Json
import io.circe.syntax._
import org.http4s._
import zio.{Task, ZIO}

case class RouteClient(route: HttpRoutes[Task])(implicit rt: zio.Runtime[zio.ZEnv]) {

  import ClientImplicits._

  def listTopics(): ZIO[Any, Throwable, Response[Task]] = {
    responseFor(RouteClient.get("/kafka/topics", "internal" -> true.toString))
  }

  def partitionsForTopics(topics: Set[String]): ZIO[Any, Throwable, WrappedResponse[Map[String, TopicDesc]]] = {
    responseFor(RouteClient.get(topics.mkString("/kafka/partitions/", ",", ""))).map(_.wrapped[Map[String, TopicDesc]])
  }

  def partitionsForTopic(topic: String, theRest: String*): ZIO[Any, Throwable, WrappedResponse[Map[String, TopicDesc]]] = {
    partitionsForTopics(theRest.toSet + topic)
  }

  def createTopic(request: CreateTopic): ZIO[Any, Throwable, Response[Task]] = {
    responseFor(RouteClient.post("/kafka/topic", request.asJson.noSpaces))
  }

  def deleteTopic(topic: String) = {
    responseFor(RouteClient.delete(s"/kafka/partitions/$topic")).map(_.wrapped[Set[String]])
  }

  def deleteTopics(topics: Set[String]) = {
    responseFor(RouteClient.post("/kafka/topics/delete", topics.asJson.noSpaces)).map(_.wrapped[Set[String]])
  }

  def describeCluster(): ZIO[Any, Throwable, WrappedResponse[Map[String, TopicDesc]]] = {
    responseFor(RouteClient.get("/kafka/cluster")).map(_.wrapped[Map[String, TopicDesc]])
  }

  def metrics(): ZIO[Any, Throwable, WrappedResponse[Map[String, List[MetricEntry]]]] = {
    responseFor(RouteClient.get("/kafka/metrics")).map(_.wrapped[Map[String, List[MetricEntry]]])
  }

  def publish(data: PublishOne): ZIO[Any, Throwable, WrappedResponse[RecordMetadataResponse]] = {
    responseFor(RouteClient.post("/kafka/publish", data.asJson.noSpaces)).map(_.wrapped[RecordMetadataResponse])
  }

  def listOffsetsAtTime(topic: String, time: String): ZIO[Any, Throwable, WrappedResponse[Seq[ListOffsetsEntry]]] = {
    responseFor(RouteClient.get(s"/kafka/offsets/$topic/$time")).map(_.wrapped[Seq[ListOffsetsEntry]])
  }

  def listOffsetsPost(request: ListOffsetsRequest): ZIO[Any, Throwable, WrappedResponse[Seq[OffsetRange]]] = {
    responseFor(RouteClient.post("/kafka/offsets", request.asJson.noSpaces)).map(_.wrapped[Seq[OffsetRange]])
  }

  def listOffsetsForTopics(topics: Set[String]): ZIO[Any, Throwable, WrappedResponse[Seq[OffsetRange]]] = {
    responseFor(RouteClient.get(topics.mkString("/kafka/offsets/", ",", ""))).map(_.wrapped[Seq[OffsetRange]])
  }


  def listGroups(): ZIO[Any, Throwable, WrappedResponse[Json]] = {
    responseFor(RouteClient.get("/kafka/groups")).map(_.wrapped[Json])
  }

  def consumerGroupStats(): ZIO[Any, Throwable, WrappedResponse[Json]] = {
    responseFor(RouteClient.get(s"/kafka/groups/stats")).map(_.wrapped[Json])
  }

  def consumerGroupDescribeTopics(topics: Set[Topic], includeAuthorizedOperations: Boolean): ZIO[Any, Throwable, WrappedResponse[Map[ConsumerGroupId, ConsumerGroupDesc]]] = {
    responseFor(RouteClient.post(s"/kafka/group/describe",
      topics.asJson.noSpaces, "includeAuthorizedOperations" -> includeAuthorizedOperations.toString))
      .map(_.wrapped[Map[ConsumerGroupId, ConsumerGroupDesc]])
  }

  def peekGet(request: PeekRequest) = {
    //partitions
    val baseUrl = request.topics.mkString("/kafka/consumer/peek/", ",", s"?from=${request.fromOffset}&limit=${request.limit}")
    val url = if (request.partitions.isEmpty) {
      baseUrl
    } else {
      request.partitions.mkString(s"$baseUrl&partitions=", ",", "")
    }
    responseFor(RouteClient.get(url)).map(_.wrapped[PeekResponse])
  }

  def peekPost(request: PeekRequest) = {
    responseFor(RouteClient.post("/kafka/consumer/peek", request.asJson.noSpaces)).map(_.wrapped[PeekResponse])
  }

  private def responseFor(request: Request[Task]) = responseForOpt(request).map(_.getOrElse(sys.error("no response")))

  private def responseForOpt(request: Request[Task]): Task[Option[Response[Task]]] = route(request).value
}

object RouteClient {

  def delete(url: String, queryParams: (String, String)*): Request[Task] = {
    Request[Task](method = Method.DELETE, uri = asUri(url, queryParams: _*))
  }

  def get(url: String, queryParams: (String, String)*): Request[Task] = {
    val uri: Uri = asUri(url, queryParams: _*)
    Request[Task](method = Method.GET, uri = uri)
  }

  def post(url: String, body: String, queryParams: (String, String)*): Request[Task] = {
    val uri: Uri = asUri(url, queryParams: _*)
    Request[Task](method = Method.POST, uri = uri).withEntity(body)
  }

  private def asUri(url: String, queryParams: (String, String)*) = {
    val encoded = Uri.encode(url)
    val uri = if (queryParams.isEmpty) {
      Uri.unsafeFromString(encoded)
    } else {
      Uri.unsafeFromString(encoded).withQueryParams(queryParams.toMap)
    }
    uri
  }

}