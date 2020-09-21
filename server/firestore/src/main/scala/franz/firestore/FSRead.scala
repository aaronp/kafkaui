package franz.firestore

import java.util.NoSuchElementException
import java.util.concurrent.atomic.AtomicBoolean

import cats.syntax.option._
import com.google.api.gax.rpc.ApiStreamObserver
import com.google.cloud.firestore.Query.Direction
import com.google.cloud.firestore.{DocumentSnapshot, Firestore, Query}
import franz.data._
import franz.data.crud.ReadRecord
import io.circe.{DecodingFailure, Json}
import zio._

object FSRead {
  def apply(): ReadRecord.Service[FS, RecordCoords, Option[VersionedJson]] = {
    instance
  }

  lazy val instance: ReadRecord.Service[FS, RecordCoords, Option[VersionedJson]] = {
    ReadRecord.liftF[FS, RecordCoords, Option[VersionedJson]] {
      case RecordCoords(collectionName, id, PreviousVersion(previousTo)) => previous(collectionName, id, previousTo)
      case RecordCoords(collectionName, id, NextVersion(afterVersion)) => next(collectionName, id, afterVersion)
      case RecordCoords(collectionName, id, ExplicitVersion(version)) => exact(collectionName, id, version)
      case RecordCoords(collectionName, id, LatestVersion) => latest(collectionName, id)
    }
  }

  def previous(collectionName: CollectionName, id: Id, previousTo: Int): ZIO[Has[Firestore], DecodingFailure, Option[VersionedRecord[Json]]] = {
    queryFirstDocumentAsVersionedJson(collectionName, id) { query =>
      query.whereLessThan(VersionedRecord.fields.Version, previousTo)
        .orderBy(VersionedRecord.fields.Version, Direction.DESCENDING)
    }
  }

  def exact(collectionName: CollectionName, id: Id, version: Int): ZIO[Has[Firestore], DecodingFailure, Option[VersionedRecord[Json]]] = {
    queryFirstDocumentAsVersionedJson(collectionName, id) { query =>
      query.whereEqualTo(VersionedRecord.fields.Version, version)
    }
  }

  def latest(collectionName: CollectionName, id: Id): ZIO[Has[Firestore], DecodingFailure, Option[VersionedRecord[Json]]] = {
    latestDoc(collectionName, id).either.flatMap {
      case Left(_) => ZIO.succeed(None)
      case Right(doc: DocumentSnapshot) => parseAsVersionedJson(doc).map(_.some)
    }
  }

  def latestDoc(collectionName: CollectionName, id: Id): ZIO[Has[Firestore], NoSuchElementException, DocumentSnapshot] = {
    queryFirstDocument(collectionName, id) { query =>
      query.orderBy(VersionedRecord.fields.Version, Direction.DESCENDING)
    }
  }

  def next(collectionName: CollectionName, id: Id, afterVersion: Int): ZIO[Has[Firestore], DecodingFailure, Option[VersionedRecord[Json]]] = {
    queryFirstDocumentAsVersionedJson(collectionName, id) { query =>
      query.whereGreaterThan(VersionedRecord.fields.Version, afterVersion)
        .orderBy(VersionedRecord.fields.Version, Direction.ASCENDING)
    }
  }

  private def queryFirstDocumentAsVersionedJson(collectionName: CollectionName, id: Id)(prepQuery: Query => Query): ZIO[Has[Firestore], DecodingFailure, Option[VersionedRecord[Json]]] = {
    queryFirstDocument(collectionName, id)(prepQuery).either.flatMap {
      case Left(_) => ZIO.succeed(None)
      case Right(doc: DocumentSnapshot) => parseAsVersionedJson(doc).map(_.some)
    }
  }

  def parseAsVersionedJson(doc: DocumentSnapshot): IO[DecodingFailure, VersionedRecord[Json]] = {
    IO.succeed(JsonToMap.from(doc.getData)).flatMap { json =>
      json.as[VersionedRecord[Json]] match {
        case Left(err) =>
          // let's try the 'latest' document
          json.hcursor.downField("latest").as[VersionedRecord[Json]] match {
            case Left(_) => IO.fail(err.withMessage(s"Error parsing '${json.noSpaces}': '${err.message}'"))
            case Right(gotIt) => IO.succeed(gotIt)
          }
        case Right(value) => IO.succeed(value)
      }
    }
  }

  /**
   * execute the query and return either the DocumentSnapshot or an error of NoSuchElementException
   *
   * @param collectionName
   * @param id
   * @param prepQuery
   * @return
   */
  private def queryFirstDocument(collectionName: CollectionName, id: Id)(prepQuery: Query => Query): ZIO[Has[Firestore], NoSuchElementException, DocumentSnapshot] = {
    getFirestore.flatMap { fs =>
      val query: Query = prepQuery(fs
        .collection(collectionName)
        .document(id)
        .collection(Versions)
        .limit(1)
      )
      execHead(query)
    }
  }

  def execHead(query: Query): ZIO[Any, NoSuchElementException, DocumentSnapshot] = {
    val task = Task.effectAsync[DocumentSnapshot] { callback =>
      query.stream(new ApiStreamObserver[DocumentSnapshot] {
        val received = new AtomicBoolean(false)

        override def onNext(value: DocumentSnapshot): Unit = {
          if (received.compareAndSet(false, true)) {
            callback(Task.succeed(value))
          }
        }

        override def onError(t: Throwable): Unit = callback(Task.fail(t))

        override def onCompleted(): Unit = {
          if (received.compareAndSet(false, true)) {
            callback(Task.fail(new NoSuchElementException))
          }
        }
      })
    }
    task.refineOrDie {
      case e: NoSuchElementException => e
    }
  }

  def execAllAsVersionedRecords(query: Query): ZIO[Any, Throwable, List[VersionedRecord[Json]]] = {
    execAll(query).toIterator.use { iter =>
      IO.foreach(iter.to(Iterable)) {
        case Left(err) => IO.fail(err)
        case Right(doc) => FSRead.parseAsVersionedJson(doc)
      }
    }
  }
  def execAll(query: Query): stream.Stream[Throwable, DocumentSnapshot] = {
    stream.Stream.effectAsync[Throwable, DocumentSnapshot] { callback =>
      query.stream(new ApiStreamObserver[DocumentSnapshot] {
        override def onNext(value: DocumentSnapshot): Unit = callback(IO.succeed(value))
        override def onError(t: Throwable): Unit = callback(IO.fail(t.some))
        override def onCompleted(): Unit = callback(IO.fail(None))
      })
    }
  }
}
