package franz.app


import java.awt.Desktop
import java.net.URI

import scala.concurrent.Future

object DevApp extends App {

  lazy val dt = Desktop.getDesktop

  dockerenv.mongo().start()

  if (Desktop.isDesktopSupported && dt.isSupported(Desktop.Action.BROWSE)) {
    import scala.concurrent.ExecutionContext.Implicits._

    Future {
      Thread.sleep(1000)
      dt.browse(new URI("http://localhost:8080"))
    }
  }

  MainEntryPoint.main(args)
}
