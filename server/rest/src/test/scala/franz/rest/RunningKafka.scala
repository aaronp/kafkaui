package franz.rest

import java.net.InetAddress
import java.nio.file.Paths

import eie.io._
import org.scalatest.BeforeAndAfterAll
import zio.Task

trait RunningKafka extends BeforeAndAfterAll {
  self: BaseTest =>

  var runningBefore = false

  override def beforeAll(): Unit = {
    super.beforeAll()
    val weStartedKafka = GoOnAheadAndJustDoubleCheckKafkaIsRunning.startIfRequired()
    runningBefore = !weStartedKafka
    awaitKafka()
    updateKafkaConfig()
  }

  def awaitKafka() = {
    val isRunning = Task(GoOnAheadAndJustDoubleCheckKafkaIsRunning.isRunning())
      .repeatWhile(_ == false)
      .timeout(java.time.Duration.ofSeconds(testTimeout.toSeconds))
      .provide(rt.environment)
      .value()

    isRunning shouldBe Some(true)
  }

  def updateKafkaConfig() = {
    val (time, ipAddress) = Task {
      val ip = GoOnAheadAndJustDoubleCheckKafkaIsRunning.myIP()
      ip
    }.timed.provide(rt.environment).value()
    val bootstrapservers = getClass.getClassLoader.getResource("bootstrapservers.conf")

    val newConf =
      s"""# Generated from RunningKafka.scala
         |# took ${time.toMillis}ms to get the IP address
         |franz.kafka.ip="${ipAddress}:9095" """.stripMargin

    val updated = Paths.get(bootstrapservers.toURI).text = newConf


    println(s"Updated $updated to:\n${newConf}")
  }

  override def afterAll(): Unit = {
    super.afterAll()
    if (!runningBefore) {
      GoOnAheadAndJustDoubleCheckKafkaIsRunning.stop()
    }
  }

}
