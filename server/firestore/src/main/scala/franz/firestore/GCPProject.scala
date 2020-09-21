package franz.firestore

import zio._
import zio.system.System

object GCPProject {

  final case class ProjectId(value: String)

  def get: URIO[HasProjectId, String] = ZIO.access[HasProjectId](_.get.value)

  def fromEnv = zio.system.env("PROJECT_ID").map(_.map(ProjectId.apply)).some

  def live(defaultProjectId: String): ZIO[system.System, Nothing, ProjectId] = {
    fromEnv.orElse(ZIO.succeed(ProjectId(defaultProjectId)))
  }

  def liveLayer(defaultProjectId: String): ZLayer[System, Nothing, Has[ProjectId]] = ZLayer.fromEffect(live(defaultProjectId))
}
