package franz.rest.kafka

import org.apache.kafka.clients.consumer.ConsumerRecord
import zio.{Has, Task, ZIO}

package object routes {

  type ConsumerGroupId = String
  type Topic = String
  type Partition = Int

  type ConsumerSvc = Has[ConsumerOps]
  type AdminSvc = Has[AdminOps]
  type ProducerSvc = Has[ProducerOps]

  /**
   * A 'peek' which will use a throw-away consumer
   *
   * @param request
   * @return
   */
  def onPeekRequest(request: PeekRequest): ZIO[Has[AdminOps], Throwable, PeekResponse] = {
    for {
      groupId <- ConsumerOps.nextGroupIdTask
      admin <- ZIO.service[AdminOps]
      result <- Task(ConsumerOps(request.topics, groupId)).bracket(_.closeTask) { consumer =>
        take(request).provide(Has(consumer) ++ Has(admin))
      }
      //TODO - this blocks the ConsumerOpsTest from completing
      //      _ <- admin.deleteGroup(Set(groupId))
    } yield PeekResponse(result.map(Record.apply))
  }

  def take(request: PeekRequest) = for {
    read <- ZIO.service[ConsumerOps]
    admin <- ZIO.service[AdminOps]
    partitionMap <- admin.partitionsForTopic(request.topics)
    partitions = partitionMap.values.flatMap(_.asTopicPartitions).filter { topicPartition =>
      request.partitions.isEmpty || request.partitions.contains(topicPartition.partition())
    }
    _ <- read.assign(partitions.toSet)
    _ <- read.seekTo(request.fromOffset)
    list <- read.take(request.limit)
  } yield list
}
