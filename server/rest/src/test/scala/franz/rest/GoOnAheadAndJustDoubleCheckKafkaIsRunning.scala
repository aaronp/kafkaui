package franz.rest

import scala.sys.process._

/**
 * http://unicodeemoticons.com/
 * https://lingojam.com/StylishTextGenerator
 * https://www.ascii-art-generator.org/
 * http://patorjk.com/software/taag/#p=display&f=Graffiti&t=Type%20Something%20
 */
object GoOnAheadAndJustDoubleCheckKafkaIsRunning {
  def main(args: Array[String]): Unit = {
    startIfRequired()
  }

  def myIP(): String = Seq("ipconfig", "getifaddr", "en0").lazyLines.head

  /**
   *
   * @return true if started, false if this call had no effect
   */
  def startIfRequired(): Boolean = {

    val isAlreadyRunning = isRunning() && {
      println(
        """          +-----------------------------------------+
          >          | ( •_•)>⌐■-■  𝕂𝕒𝕗𝕜𝕒 𝔸𝕝𝕣𝕖𝕒𝕕𝕪 ℝ𝕦𝕟𝕟𝕚𝕟𝕘  (⌐■_■) |
          >          +-----------------------------------------+
          >""".stripMargin('>'))
      true
    }

    val started = isAlreadyRunning || {
      println(
        """          +------------------+
          >          | ᔕTᗩᖇTIᑎG KᗩᖴKᗩ |
          >          +------------------+
          >""".stripMargin('>'))
      start().nonEmpty
    }

    !isAlreadyRunning && started
  }

  def dockerProcessOutput(): Seq[String] = Seq("docker", "ps").lazyLines.toList

  /**
   * Sample output:
   * {{{
   * $ docker ps
   * CONTAINER ID        IMAGE                       COMMAND                  CREATED             STATUS              PORTS                                                NAMES
   * 3b8130d89c69        wurstmeister/kafka:latest   "start-kafka.sh"         47 seconds ago      Up 46 seconds       0.0.0.0:9096->9096/tcp                               server_kafka2_1
   * da391313f57b        wurstmeister/kafka:latest   "start-kafka.sh"         47 seconds ago      Up 47 seconds       0.0.0.0:9095->9095/tcp                               server_kafka1_1
   * 11ae2b98604e        wurstmeister/kafka:latest   "start-kafka.sh"         52 seconds ago      Up About a minute   0.0.0.0:9094->9094/tcp                               server_kafka0_1
   * 707eda1faf89        wurstmeister/zookeeper      "/bin/sh -c '/usr/sb…"   40 hours ago        Up 52 seconds       22/tcp, 2888/tcp, 3888/tcp, 0.0.0.0:2181->2181/tcp   server_zookeeper_1
   * }}}
   */
  private val RunningR = s".*Up .* 0.0.0.0:([0-9]+)->[0-9]+/tcp.*".r

  def upPorts(): Seq[Int] = dockerProcessOutput().collect {
    case RunningR(p1) => p1.toInt
  }

  def start() = Seq("./start-kafka-cluster.sh").!!

  def dockerPsQuiet() = Seq("docker", "ps", "-q").lazyLines_!.toList

  def stop(processes: Seq[String] = dockerPsQuiet()) = {
    println(
      """
        >        +--------------------------+
        >        |  🆂🆃🅾🅿🅿🅸🅽🅶 🅺🅰🅵🅺🅰  |
        >        +--------------------------+
        >""".stripMargin('>'))
    (Seq("docker", "kill") ++ processes).!!
  }

  def isRunning(): Boolean = {
    val ports = upPorts()
    Set(9094, 9095, 9096).forall(ports.contains)
  }
}
