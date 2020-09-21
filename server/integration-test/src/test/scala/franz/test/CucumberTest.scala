package franz.test

import io.cucumber.junit.{Cucumber, CucumberOptions}
import org.junit.runner.RunWith

@RunWith(classOf[Cucumber])
@CucumberOptions(
  features = Array("classpath:"),
  glue = Array("classpath:franz.test.steps"),
  strict = true,
  dryRun = false,
//  tags = Array("@debug"),
  plugin = Array("pretty", "html:target/cucumber", "json:target/cucumber/test-report.json", "junit:target/cucumber/test-report.xml")
)
class CucumberTest
