package franz

import cats.Parallel
import cats.effect.Sync
import com.typesafe.config.Config

object AppServicesJVM {

  def tooManyValuesThreshold(config: Config): Int = config.getInt("franz.index.tooManyValuesThreshold")

  def requiredContiguousVersions(config: Config): Boolean = config.getBoolean("franz.index.requiredContiguousVersions")

  def inMemory[F[_] : Sync : Parallel](config: Config): AppServices[F] = {
    AppServices.inMemory(requiredContiguousVersions(config), tooManyValuesThreshold(config))
  }

}
