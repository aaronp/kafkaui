package franz

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.{Firestore, FirestoreOptions}
import franz.firestore.GCPProject.ProjectId
import zio._
import zio.blocking.Blocking

package object firestore {

  val Versions = "versions"
  type HasProjectId = Has[ProjectId]
  type HasCreds = Has[GoogleCredentials]
  type HasOptions = Has[FirestoreOptions]
  type HasFirestore = Has[Firestore]
  type FSEnv = HasFirestore with Blocking

  object FSEnv {
    def live: ZLayer[Any, Throwable, FSEnv] = FirestoreLayer.live ++ ZEnv.live
  }

  type FS[A] = RIO[FSEnv, A]

  def getBlocking: URIO[Blocking, Blocking.Service] = URIO.access[Blocking](_.get)

  def getFirestore: URIO[Has[Firestore], Firestore] = URIO.access[Has[Firestore]](_.get)

  def fsEnv: ZIO[FSEnv, Nothing, (Blocking.Service, Firestore)] =
    for {
      blocking <- getBlocking
      fs <- getFirestore
    } yield {
      (blocking, fs)
    }

}
