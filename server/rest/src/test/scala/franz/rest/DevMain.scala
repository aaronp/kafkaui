package franz.rest

import java.awt.Desktop
import java.net.URI

import scala.concurrent.Future

object DevMain extends App {

  lazy val dt = Desktop.getDesktop

  if (Desktop.isDesktopSupported && dt.isSupported(Desktop.Action.BROWSE)) {
    import scala.concurrent.ExecutionContext.Implicits._

    Future {
      Thread.sleep(1000)
      dt.browse(new URI("http://localhost:8080"))
    }
  }

  val useCats = args.contains("cats")
  if (useCats) {
    MainIO.main(args)
  } else {
    MainZIO.main(args)
  }
}
