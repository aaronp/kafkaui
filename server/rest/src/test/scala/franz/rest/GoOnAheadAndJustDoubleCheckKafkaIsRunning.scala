package franz.rest


object GoOnAheadAndJustDoubleCheckKafkaIsRunning extends App {

  def apply() = {
    import sys.process._
    import eie.io._


    val result: String = Seq("./start-kafka-cluster.sh").!!  //.asPath.toFile.cat.!!
    println(result)
  }

  apply()
}
