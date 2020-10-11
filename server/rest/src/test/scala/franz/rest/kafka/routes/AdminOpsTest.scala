package franz.rest.kafka.routes

import franz.rest.{BaseTest, RunningKafka}
import zio.Task

class AdminOpsTest extends BaseTest with RunningKafka {

  "AdminOps.updatePartitions" should {
    "be able to updatePartitions" in {
      val newTopic = "update" + nextTopic()

      val test = Task(AdminOps()).bracket(_.closeTask) { admin =>
        val created: AdminOps.CreateTopic = admin.createTopic(AdminOps.CreateTopic(newTopic, 1, 1)).value()


        for {
          _ <- Task {
            val created = admin.createTopic(AdminOps.CreateTopic(newTopic, 1, 1)).value()
            created shouldBe AdminOps.CreateTopic(newTopic, 1, 1)
            created
          }
          initialPartitions <- admin.partitionsForTopic(Set(newTopic))
          _ <- admin.updatePartitions(AlterPartitionRequest())
          newPartitions <- admin.partitionsForTopic(Set(newTopic))
        } yield (initialPartitions, newPartitions)
      }


      val (initialPartitions, newPartitions) = test.value()
      println(initialPartitions)
      println(newPartitions)
    }
  }
  "AdminOps.topics" should {
    "be able create and read back topics" in {
      val newTopic = "two" + nextTopic()

      val test = Task(AdminOps()).bracket(_.closeTask) { admin =>
        for {
          _ <- Task {
            val created = admin.createTopic(AdminOps.CreateTopic(newTopic, 2, 3)).value()
            created shouldBe AdminOps.CreateTopic(newTopic, 2, 3)
            created
          }
          topics <- admin.topics(true)
          partitions <- admin.partitionsForTopic(Set(newTopic))
        } yield (topics, partitions)
      }

      val (map, partitions) = test.value()
      partitions.size shouldBe 1
      map should not be (empty)
      map.get(newTopic) shouldBe Some(false)
      partitions(newTopic).partitions.map(_.partition) shouldBe Seq(0, 1)
    }
  }
}
