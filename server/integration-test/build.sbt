import eie.io._

enablePlugins(CucumberPlugin)
CucumberPlugin.glues := List("franz/test/steps")
CucumberPlugin.cucumberTestReports := "target/cucumber".asPath.toFile
