package franz.firestore

import scala.concurrent.{ExecutionContext, Future}
import scala.sys.process._
import scala.util.{Properties, Try}

object GCloud {

  def projectId() = goodleIt("http://metadata.google.internal/computeMetadata/v1/project/project-id")
  def instanceId() = goodleIt("http://metadata.google.internal/computeMetadata/v1/instance/id")
  def instanceName() = goodleIt("http://metadata.google.internal/computeMetadata/v1/instance/name")
  def machineType() = goodleIt("http://metadata.google.internal/computeMetadata/v1/instance/machine-type")
  def serviceAccounts() = goodleIt("http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/")

  private def goodleIt(url : String): String = {
    requests.get(url, headers = List("Metadata-Flavor" -> "Google")).text()
  }

  /**
   * https://cloud.google.com/compute/docs/storing-retrieving-metadata
   * https://googleapis.github.io/google-cloud-dotnet/docs/index.html
   *
   * @param ec
   * @return
   */
  def googleDesc(implicit ec : ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global): Future[List[(String, String)]] = {
    def d(name : String)(value : String) = {
      println(s"!!! $name got $value")
      (name, value)
    }
    val futures = List(
      Future(projectId()).map(d("projectId")),
      Future(instanceId()).map(d("instanceId")),
      Future(machineType()).map(d("machineType")),
      Future(serviceAccounts()).map(d("serviceAccounts"))
    )
    Future.sequence(futures)
  }

  def chomp(out: String) = out.linesIterator.map(_.trim).filterNot(_.isEmpty).mkString("\n")

  def whichCloud() = chomp("which gcloud".!!)

  def whoami() = chomp("""gcloud config list account --format "value(core.account)"""".!!)

  import eie.io._

  lazy val dotConfPath = s"${Properties.userHome}/.config".asPath

  def dotConf: String = {
    if (dotConfPath.exists()) {
      val tree = dotConfPath.renderTree
      s"$dotConfPath:\n$tree"
    } else {
      "./.config doesn't exist"
    }
  }

  def debug() = {
    val tri = Try(
      s"""              which gcloud : ${whichCloud()}
         |gcloud config list account : ${whoami()}""".stripMargin)

    s"""${Try(dotConf)}
       |$tri
       |""".stripMargin
  }
}
