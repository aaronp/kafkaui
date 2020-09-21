package franz.firestore

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.{Firestore, FirestoreOptions}
import com.typesafe.scalalogging.StrictLogging
import franz.firestore.GCPProject.ProjectId
import zio._

import scala.util.Properties

object FirestoreLayer extends StrictLogging {


  // TODO
  // FIXME - close/fix this resource leak
  def FIXME(firestore: Firestore) = {
    //      firestore.close()
  }

  def liveProjectLayer = ZEnv.live >>> GCPProject.liveLayer("sandbox-279208")

  def live: ZLayer[Any, Throwable, Has[Firestore]] = {
    val credsAndProject = (FirestoreLayer.credentials.credentialsLayer ++ liveProjectLayer).map { env: Has[GoogleCredentials] with Has[ProjectId] =>
      val project = env.get[GCPProject.ProjectId]
      val c = env.get[GoogleCredentials]
      firestoreOptions(project.value, c)
    }

    credsAndProject >>> ZLayer.fromFunctionManaged(managedFirestore)
  }

  object credentials {
    def live: Task[GoogleCredentials] = {
      logger.info(s"Creating live credentials, I am '${Properties.userName}', glcoud: ${GCloud.debug()} w/ env:${sys.env.mkString("\n\t", "\n\t", "\n\n")}")
      ZIO.effect(GoogleCredentials.getApplicationDefault).map { credential: GoogleCredentials =>
        logger.info(s"Got GCP credentials $credential , credential.createScopedRequired is ${credential.createScopedRequired}")

        if (credential.createScopedRequired)
          credential.createScoped(java.util.Arrays.asList("https://www.googleapis.com/auth/cloud-platform"))
        else
          credential
      }
    }

    lazy val credentialsLayer: ZLayer[Any, Throwable, Has[GoogleCredentials]] = ZLayer.fromEffect(live)
  }

  private def firestoreOptions(project: String, c: GoogleCredentials): FirestoreOptions = FirestoreOptions.getDefaultInstance().toBuilder().setProjectId(project)
    .setCredentials(c)
    .build()

  def managedFirestore(options: FirestoreOptions): Managed[Throwable, Firestore] = {
    val description = s"firestore DB '${options.getDatabaseId}', application '${options.getApplicationName}', project '${options.getProjectId}'"
    Managed.makeEffect {
      logger.info(s"Connecting to $description")
      options.getService
    } { fs =>
      logger.warn(s"Closing firestore ${description}")

      FIXME(fs)
    }
  }
}
