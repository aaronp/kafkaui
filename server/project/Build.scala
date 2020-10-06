import java.nio.file.Path

import eie.io._
import sbt._

object Build {

  val ProjectName = "franz"

  object deps {

    val Http4sVersion = "0.21.7"

    val typesafeConfig: ModuleID = "com.typesafe" % "config" % "1.4.0"

    val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2"
    val logback = "ch.qos.logback" % "logback-classic" % "1.2.3"
    val logging = List(scalaLogging, logback, "org.slf4j" % "slf4j-api" % "1.7.30")

    val zioVersion = "1.0.1"
    val zio = List(
      "dev.zio" %% "zio-interop-cats" % "2.2.0.0",
      "dev.zio" %% "zio" % zioVersion,
      "dev.zio" %% "zio-streams" % zioVersion,
      "dev.zio" %% "zio-test" % zioVersion % "test",
      "dev.zio" %% "zio-test-sbt" % zioVersion % "test")

    val circeVersion = "0.13.0"
    val circeGenExtrasVersion = "0.13.0"
    val circe = {
      List(
        "io.circe" %% "circe-generic" % circeVersion,
        "io.circe" %% "circe-generic-extras" % circeGenExtrasVersion,
        "io.circe" %% "circe-parser" % circeVersion,
        "io.circe" %% "circe-literal" % circeVersion % Test
      )
    }

    val scalaTest = "org.scalatest" %% "scalatest" % "3.2.2" % "test"

    def clientJVM = {
      List(
        typesafeConfig,
        scalaTest,
        "com.github.aaronp" %% "kafka4m" % "0.7.4-SNAPSHOT",
        "com.github.aaronp" %% "args4c" % "0.7.0",
        "com.github.aaronp" %% "eie" % "1.0.0",
        "org.http4s" %% "http4s-circe" % Http4sVersion,
        "org.http4s" %% "http4s-core" % Http4sVersion,
        "org.scala-js" %% "scalajs-stubs" % "1.0.0" % "provided",
        "org.pegdown" % "pegdown" % "1.6.0" % Test
      ) ++ logging.map(_ % Test)
    }

    def application: List[ModuleID] = {
      // List("dev.zio" %% "zio-interop-monix" % "3.1.0.0-RC2")
      Nil
    }

    def rest: List[ModuleID] = {
      zio ++
        logging ++
        circe ++
        Seq(
          typesafeConfig,
          "org.http4s" %% "http4s-blaze-server" % Http4sVersion,
          "org.http4s" %% "http4s-circe" % Http4sVersion,
          "org.http4s" %% "http4s-blaze-client" % Http4sVersion,
          "org.http4s" %% "http4s-dsl" % Http4sVersion,
          scalaTest,
          "org.specs2" %% "specs2-core" % "4.9.4" % Test
        )
    }

    def cucumber = {
      val cs = "io.cucumber" %% "cucumber-scala" % "6.8.0" % "test"

      cs +: Seq("cucumber-core", "cucumber-jvm", "cucumber-junit").map { art =>
        "io.cucumber" % art % "6.8.0" % "test"
      }
    }
  }

  def scalacSettings = {
    List(
      "-deprecation", // Emit warning and location for usages of deprecated APIs.
      "-encoding",
      "utf-8", // Specify character encoding used by source files.
      "-feature", // Emit warning and location for usages of features that should be imported explicitly.
      "-language:reflectiveCalls", // Allow reflective calls
      "-language:higherKinds", // Allow higher-kinded types
      "-language:implicitConversions", // Allow definition of implicit functions called views
      "-unchecked",
      "-language:reflectiveCalls", // Allow reflective calls
      "-language:higherKinds", // Allow higher-kinded types
      "-language:implicitConversions" // Allow definition of implicit functions called views
    )

  }

  case class DockerResources(deployResourceDir: Path, //
                             jsArtifacts: Seq[Path], //
                             webResourceDir: Path, //
                             restAssembly: Path, //
                             targetDir: Path) {
    def moveResourcesToDeployDir(logger: sbt.util.Logger) = {
      logger.info(
        s""" Building Docker Image with:
           |
           |   deployResourceDir = ${deployResourceDir.toAbsolutePath}
           |   jsArtifacts       = ${jsArtifacts.map(_.toAbsolutePath).mkString("[", ",", "]")}
           |   webResourceDir    = ${webResourceDir.toAbsolutePath}
           |   restAssembly      = ${restAssembly.toAbsolutePath}
           |   targetDir         = ${targetDir.toAbsolutePath}
           |
       """.stripMargin)

      val jsDir = targetDir.resolve("web/js").mkDirs()
      IO.copyDirectory(deployResourceDir.toFile, targetDir.toFile)
      IO.copyDirectory(webResourceDir.toFile, targetDir.resolve("web").toFile)
      IO.copy(List(restAssembly.toFile -> (targetDir.resolve("app.jar").toFile)))
      IO.copy(jsArtifacts.map(jsFile => jsFile.toFile -> (jsDir.resolve(jsFile.fileName).toFile)))
      webResourceDir.resolve("css/touch.css").text = ""
      webResourceDir.resolve("img/touch.css").text = ""
      this
    }
  }


  def docker(resources: DockerResources) = {
    execIn(resources.targetDir, "docker", "build", s"--tag=${ProjectName}", ".")
  }

  def execIn(inDir: Path, cmd: String*): Unit = {
    import scala.sys.process._
    val p: ProcessBuilder = Process(cmd.toSeq, inDir.toFile)
    val retVal = p.!
    require(retVal == 0, cmd.mkString("", " ", s" in dir ${inDir} returned $retVal"))
  }
}
