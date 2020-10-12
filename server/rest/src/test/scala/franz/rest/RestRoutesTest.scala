package franz.rest

import franz.rest.kafka.routes.AdminOps.CreateTopic
import franz.rest.kafka.routes._
import org.http4s.{HttpRoutes, Response}
import zio.{Task, UIO, ZIO}

import scala.util.Success

class RestRoutesTest extends BaseRouteTest with RunningKafka {

  "RestRoutes.routes" should {
    "GET /kafka/topics to list topics" in {
      val test: ZIO[Any, Throwable, Response[Task]] = withRoutesFlatten { routeUnderTest =>
        RouteClient(routeUnderTest).listTopics()
      }
      val response = test.value()
      response.status.code shouldBe 200
      val Success(topics) = response.bodyAs[Map[String, Boolean]]
      topics should not be (null)
    }

    "POST /kafka/topic to create a topic" in {
      val topic = nextTopic()
      val test = withRoutesFlatten { routeUnderTest =>
        for {
          _ <- RouteClient(routeUnderTest).createTopic(CreateTopic(topic, 2, 2))
          listResp <- RouteClient(routeUnderTest).listTopics()
          partitionResp <- RouteClient(routeUnderTest).partitionsForTopic(topic)
        } yield (listResp, partitionResp)
      }
      val (listResp, partitionResp) = test.value()
      listResp.status.code shouldBe 200
      val Success(topics) = listResp.bodyAs[Map[String, Boolean]]
      topics.keySet should contain(topic)

      val Success(partitions: Map[String, TopicDesc]) = partitionResp.parsed
      partitions(topic).partitions.map(_.partition) should contain only(0, 1)
    }

    "GET /kafka/consumer/peek to read data" in {
      val topic = nextTopic()
      val test = withRoutesFlatten { routeUnderTest =>
        val client = RouteClient(routeUnderTest)
        for {
          _ <- client.createTopic(CreateTopic(topic, 2, 2))
          p1 <- client.publish(PublishOne(topic, "finley", "one"))
          p2 <- client.publish(PublishOne(topic, "quaye", "two"))
          read1 <- client.peekPost(PeekRequest(topic, 0, 10, Set.empty)).repeatUntil(_.parsed.map(_.records.size) == Success(2))
          read2 <- client.peekGet(PeekRequest(topic, 0, 10, Set.empty)).repeatUntil(_.parsed.map(_.records.size) == Success(2))
          offsets1 <- client.listOffsetsForTopics(Set(topic))
          earliest <- client.listOffsetsAtTime(topic, "earliest")
          latest <- client.listOffsetsAtTime(topic, "latest")
        } yield (p1, p2, read1, read2, offsets1, earliest, latest)
      }
      val (p1, p2, read1, read2, offsets1, earliest, latest) = test.value()

      val Seq(first, last) = offsets1.parsed.get
      first.topic shouldBe topic
      first.earliest.offset shouldBe 0
      first.latest.offset shouldBe 1

      last.topic shouldBe topic
      last.earliest.offset shouldBe 0
      last.latest.offset shouldBe 1

      val Seq(earliest1, earliest2) = earliest.parsed.get
      withClue(s"earliest1=$earliest1, earliest2=$earliest2") {
        earliest1.offset.offset shouldBe 0
        earliest2.offset.offset shouldBe 0
        if (earliest1 == TopicKey(topic, 0)) {
          earliest1.topic shouldBe TopicKey(topic, 0)
          earliest2.topic shouldBe TopicKey(topic, 1)
        } else {
          earliest1.topic shouldBe TopicKey(topic, 1)
          earliest2.topic shouldBe TopicKey(topic, 0)
        }
      }


      val Seq(lastest1, latest2) = latest.parsed.get
      withClue(s"lastest1=$lastest1, latest2=$latest2") {
        lastest1.offset.offset shouldBe 1
        latest2.offset.offset shouldBe 1

        if (lastest1.topic == TopicKey(topic, 0)) {
          lastest1.topic shouldBe TopicKey(topic, 0)
          latest2.topic shouldBe TopicKey(topic, 1)
        } else {
          lastest1.topic shouldBe TopicKey(topic, 1)
          latest2.topic shouldBe TopicKey(topic, 0)
        }
      }


      val push1 = p1.parsed.get
      push1.offset shouldBe 0
      push1.topicPartition.partition shouldBe 0

      val push2 = p2.parsed.get
      push2.offset shouldBe 0
      push2.topicPartition.partition shouldBe 1

      val getRecords = read1.parsed.get
      val postRecords = read2.parsed.get
      getRecords.records.size shouldBe 2
      postRecords.records.size shouldBe 2
    }

    "GET /kafka/metrics to read cluster metrics" in {
      val topic = nextTopic()
      val test = withRoutesFlatten { routeUnderTest =>
        RouteClient(routeUnderTest).metrics()
      }
      val metrics: WrappedResponse[Map[String, List[MetricEntry]]] = test.value()
      println(metrics)
    }
    "GET /kafka/cluster to read cluster data" in {
      val topic = nextTopic()
      val test = withRoutesFlatten { routeUnderTest =>
        RouteClient(routeUnderTest).describeCluster()
      }
      val clusterData = test.value()
      println(clusterData)
    }
    "GET /kafka/group/stats/<group> to read a consumer group stats" in {
      val test = withRoutesFlatten { routeUnderTest =>
        RouteClient(routeUnderTest).consumerGroupStats()
      }
      val clusterData = test.value()
      println(clusterData)
    }
  }

  def withRoutes[A](test: HttpRoutes[Task] => A): ZIO[Any, Throwable, A] = {
    def runTest(ops: ProducerOps) = RestRoutes().provide(ops).map(test)

    Task(ProducerOps())
      .bracket(svc => UIO(svc.close()), svc => runTest(svc))
  }

  def withRoutesFlatten[A](test: HttpRoutes[Task] => Task[A]): ZIO[Any, Throwable, A] = {
    def runTest(ops: ProducerOps) = RestRoutes().provide(ops).map(test)

    Task(ProducerOps())
      .bracket(svc => UIO(svc.close()), svc => runTest(svc).flatten)
  }
}
