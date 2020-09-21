package franz.test.steps

import cats.syntax.option._
import com.typesafe.scalalogging.StrictLogging
import dockerenv.DockerEnv
import franz.db.impl.VersionedRecordsMongo
import franz.test.steps.TestState.Running
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import sttp.client.{HttpURLConnectionBackend, _}
import sttp.model.Header

import scala.concurrent.Future

object TestState {

  case class Running(db: VersionedRecordsMongo, webServiceExitCode: Future[Int])

  // we keep this global-level state across all cucumber tests; we don't restart the server between features
  private var runningApp: Option[Running] = None
}

/**
 * Immutable data structure which keeps state across steps
 *
 * @param responses
 * @param headers
 * @param mongoInstance
 * @param hostPort
 * @param latestToken
 */
case class TestState(responses: List[Response[Either[String, String]]] = List(),
                     headers: Map[String, String] = Map.empty,
                     mongoInstance: DockerEnv.Instance = dockerenv.mongo(),
                     hostPort: String = "http://localhost:8080",
                     latestToken: Option[String] = None
                    ) extends Matchers with ScalaFutures with StrictLogging {
  implicit val backend = HttpURLConnectionBackend()

  private lazy val runningMongo = mongoInstance.start()

  def verifyDatabaseState(dbStatePath: String): Unit = {
    runningMongo shouldBe true
    val database: VersionedRecordsMongo = TestState.runningApp.collect {
      case Running(db, _) => db
    }.getOrElse(fail("App wasn't started - !?"))
    VerifyDatabase(database, dbStatePath)
  }

  def initialiseDatabase(dbStatePath: String): TestState = {
    runningMongo shouldBe true
    TestState.runningApp match {
      case Some(Running(db, _)) =>
        // we're already running - swap out the database in-place
        SnapshotsForDir.swapOutDatabase(db, dbStatePath)
        this
      case None =>
        val running = StartService(dbStatePath).futureValue
        TestState.runningApp = running.some
        this
    }
  }

  def popResponse(): (TestState, Response[Either[String, String]]) = {
    responses match {
      case head :: tail => copy(responses = tail) -> head
      case List() => fail("step wanted us to pop a response, but there are none")
    }
  }

  def makePostRequest(route: String, body: String): TestState = {
    val concat = s"$hostPort$route"
    val request = prepHeaders(headers).foldLeft(basicRequest.post(uri"$concat").body(body)) {
      case (r, (k, v)) => r.header(k, v)
    }
    logger.debug("Making POST request: " + request)
    val r = request.send()
    pushResponse(r)
  }

  def makeGetRequest(route: String): TestState = {
    val concat = s"$hostPort$route"
    val request = prepHeaders(headers).foldLeft(basicRequest.get(uri"$concat")) {
      case (r, (k, v)) => r.header(k, v)
    }
    logger.debug("Making GET request: " + request)
    val r = request.send()
    pushResponse(r)
  }

  private val BearerR = "Bearer (.*)".r

  private def pushResponse(response: Response[Either[String, String]]) = {
    val tokenOpt = response.headers.collectFirst {
      case Header("Authorization", BearerR(jwt)) => jwt
    }
    copy(responses = response :: responses, latestToken = tokenOpt.orElse(latestToken))
  }

  def prepHeaders(h: Map[String, String]): Map[String, String] = {
    latestToken.fold(h) { jwt =>
      h.updated("Authorization", s"Bearer ${jwt}")
        .updated("X-Access-Token", jwt)
    }
  }
}
