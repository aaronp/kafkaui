import java.nio.file.Path

import sbt.KeyRanks
import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}

import scala.collection.immutable

scalatex.SbtPlugin.projectSettings

val username = "aaronp"
val repo = Build.ProjectName
val defaultScalaVersion = "2.13.2"
val scalaVersions = Seq("2.13.2")

ThisBuild / scalaVersion := defaultScalaVersion
ThisBuild / organization := "com.github.aaronp"
ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-language:higherKinds",
  "-language:postfixOps",
  "-feature"
)

name := repo


val commonSettings: Seq[Def.Setting[_]] = Seq(
  organization := s"com.github.${username}",
  scalaVersion := defaultScalaVersion,
  resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  autoAPIMappings := true,
  exportJars := false,
  crossScalaVersions := scalaVersions,
  //libraryDependencies ++= Build.dtestDependencies,
  javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
  scalacOptions ++= Build.scalacSettings,
  buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
  buildInfoPackage := s"${repo}.build",
  test in assembly := {},
  assemblyMergeStrategy in assembly := {
    case str if str.contains("application.conf") => MergeStrategy.discard
    case str if str.contains("module-info.class") => MergeStrategy.first
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  },
  libraryDependencies += "com.vladsch.flexmark" % "flexmark-all" % "0.35.10" % Test,
  // see http://www.scalatest.org/user_guide/using_scalatest_with_sbt
  (testOptions in Test) += (Tests.Argument(TestFrameworks.ScalaTest, "-l", "franz.IntegrationTest", "-h", s"target/scalatest-reports-${name.value}", "-o"))
)

test in assembly := {}

// don't publish the root artifact
publishArtifact := false

publishMavenStyle := true

lazy val root = (project in file("."))
  .aggregate(
    rest,
    application,
    deploy,
    clientJS,
    clientJVM,
    db,
    firestore,
    integrationTest
  )

lazy val db = (project in file("db"))
  .settings(name := "db")
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= Build.deps.db)
  .settings(parallelExecution in Test := false)
  .dependsOn(clientJVM % "compile->compile;test->test")

lazy val firestore = (project in file("firestore"))
  .settings(name := "firestore")
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= Build.deps.firestore)
  .settings(parallelExecution in Test := false)
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))
  .dependsOn(clientJVM % "compile->compile;test->test")

lazy val rest = (project in file("rest"))
  .settings(
    name := "rest",
    libraryDependencies ++= Build.deps.rest,
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
  )
  .settings(commonSettings: _*)
  .dependsOn(clientJVM % "compile->compile;test->test")
  .dependsOn(db % "compile->compile;test->test")

lazy val deploy = project
  .in(file("deploy"))
  .settings(commonSettings: _*)
  .settings(name := "deploy")
  .dependsOn(rest % "compile->compile;test->test")

lazy val integrationTest = project
  .in(file("integration-test"))
  .settings(name := "integration-test")
  .settings(libraryDependencies ++= Build.deps.cucumber)
  .settings(commonSettings: _*)
  .dependsOn(application % "compile->compile;test->test")

lazy val clientCross = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .withoutSuffixFor(JVMPlatform)
  .in(file("client"))
  .settings(
    name := "client",
    libraryDependencies ++= Build.deps.monix.map { art =>
      "io.monix" %%% art % Build.deps.monixVersion
    },
    //https://dzone.com/articles/5-useful-circe-feature-you-may-have-overlooked
    libraryDependencies ++= List(
      "com.github.aaronp" %%% "donovan" % "0.14.0",
      "io.circe" %%% "circe-generic" % Build.deps.circeVersion,
      "io.circe" %%% "circe-generic-extras" % Build.deps.circeGenExtrasVersion,
      "io.circe" %%% "circe-parser" % Build.deps.circeVersion,
      "io.circe" %%% "circe-literal" % Build.deps.circeVersion % Test
    )
  )
  .jvmSettings(commonSettings: _*)
  .jvmSettings(name := "client")
  .jvmSettings(
    name := "client-jvm",
    coverageMinimum := 68,
    coverageFailOnMinimum := true,
    libraryDependencies ++= Build.deps.clientJVM,
    // put scaladocs under 'api/latest'
    siteSubdirName in SiteScaladoc := "api/latest"
  )
  .jsSettings(name := "client-js")
  .jsSettings(test := {}) // ignore JS tests - they're all done on the JVM
  .jsSettings(libraryDependencies ++= List(
    "org.scala-js" %%% "scalajs-java-time" % "1.0.0",
    "com.lihaoyi" %%% "scalatags" % "0.9.1",
    "org.scala-js" %%% "scalajs-dom" % "1.0.0",
    "org.scalatest" %%% "scalatest" % "3.1.2" % "test"
  ))

lazy val clientJVM = clientCross.jvm
lazy val clientJS = clientCross.js

lazy val application = project
  .in(file("application"))
  .settings(commonSettings: _*)
  .settings(name := "application")
  .settings(libraryDependencies ++= Build.deps.application)
  .dependsOn(rest % "compile->compile;test->test")
  .dependsOn(clientJVM % "compile->compile;test->test")
  .dependsOn(db % "compile->compile;test->test")
  .dependsOn(firestore % "compile->compile;test->test")


lazy val cloudBuild = taskKey[Build.DockerResources]("Prepares the app for containerisation").withRank(KeyRanks.APlusTask)

cloudBuild := {
  import eie.io._
  val appAssembly = (assembly in(application, Compile)).value

  // contains the docker resources
  val deployResourceDir = (resourceDirectory in(deploy, Compile)).value.toPath

  // contains the web resources
  val webResourceDir: Path = (resourceDirectory in(rest, Compile)).value.toPath.resolve("web")

  val fullOptPath = (fullOptJS in(clientJS, Compile)).value.data.asPath
  val fastOptPath = (fastOptJS in(clientJS, Compile)).value.data.asPath

  val jsArtifacts: immutable.Seq[Path] = {
    val dependencyFiles = fullOptPath.getParent.find(_.fileName.endsWith("-jsdeps.min.js")).toList
    fullOptPath :: dependencyFiles
  }

  val dir = baseDirectory.value / "target" / "docker"
  val dockerTargetDir = dir.toPath.mkDirs()

  val resources = Build.DockerResources(deployResourceDir = deployResourceDir, //
    jsArtifacts = jsArtifacts, //
    webResourceDir = webResourceDir, //
    restAssembly = appAssembly.asPath, //
    targetDir = dockerTargetDir
  )
  resources.moveResourcesToDeployDir(sLog.value)
}


lazy val docker = taskKey[Unit]("Packages the app in a docker file").withRank(KeyRanks.APlusTask)

// see https://docs.docker.com/engine/reference/builder
docker := {
  Build.docker(cloudBuild.value)
}


// see https://leonard.io/blog/2017/01/an-in-depth-guide-to-deploying-to-maven-central/
pomIncludeRepository := (_ => false)

// To sync with Maven central, you need to supply the following information:
pomExtra in Global := {
  <url>https://github.com/$
    {username}
    /$
    {repo}
  </url>
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      </license>
    </licenses>
    <developers>
      <developer>
        <id>$
          {username}
        </id>
        <name>Aaron Pritzlaff</name>
        <url>https://github.com/$
          {username}
          /$
          {repo}
        </url>
      </developer>
    </developers>
}
