package franz.rest

import org.http4s.dsl.Http4sDsl
import zio.{Runtime, Task, ZEnv}

package object routes {

  val taskDsl: Http4sDsl[Task] = Http4sDsl[Task]

  type EnvRuntime = Runtime[ZEnv]
}
