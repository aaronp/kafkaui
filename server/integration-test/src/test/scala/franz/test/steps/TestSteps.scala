package franz.test.steps

import franz.data.diff.{Diff, RecordDiff}
import io.cucumber.scala.{EN, ScalaDsl}

/**
 * The step grammar for our cucumber steps (which are generated from the
 */
class TestSteps extends IntegrationAssertions with ScalaDsl with EN {
  var state = TestState()

  Given("""a database in the initial state like {string}""") { dbStatePath: String =>
    state = state.initialiseDatabase(dbStatePath)
  }
  When("""The user GETs request {string}""") { path: String =>
    state = state.makeGetRequest(path)
  }
  When("""The user POSTs request {string} with json""") { (route: String, body: String) =>
    state = state.makePostRequest(route, body)
  }
  Then("""they should get a {int} response with json""") { (statusCode: Int, bodyString: String) =>
    val (newState, response) = state.popResponse()
    state = newState
    withClue(
      s"""Expected Response:
         | >>>${bodyString}<<<
         |
         |Expected Response as Json:
         | >>>${AsJson(bodyString)}<<<
         |
         |Actual Response:
         | >>>${response.body}<<<
         |
         |Actual Response as Json:
         | >>>${AsJson(response.body)}<<<
         |
         |Diff:
         |${RecordDiff(AsJson(bodyString), AsJson(response.body)).mkString("\n")}
         |""".stripMargin) {
      response.code.code shouldBe statusCode
      AsJson(response.body) shouldBe AsJson(bodyString)
    }
  }
  Then("""the database should look like {string}""") { dbStatePath: String =>
    state.verifyDatabaseState(dbStatePath)
  }

  def trim(s: String) = s.linesIterator.map(_.trim).filterNot(_.isEmpty).mkString("\n")

}
