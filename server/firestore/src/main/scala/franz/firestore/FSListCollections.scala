package franz.firestore

import franz.data.crud.ListRecords
import franz.data.{CollectionName, QueryRange}

import scala.jdk.CollectionConverters._

object FSListCollections {
  def apply(): ListRecords.Service[FS, List[CollectionName]] = instance

  lazy val instance: ListRecords.Service[FS, List[CollectionName]] = {
    ListRecords.liftF[FS, List[CollectionName]] { range: QueryRange =>
      getFirestore.map { fs =>
        range
          .fromIterable(fs.listCollections().asScala)
          .map(_.getId)
          .toList
      }
    }
  }
}
