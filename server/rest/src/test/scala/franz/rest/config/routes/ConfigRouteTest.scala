package franz.rest.config.routes

import com.typesafe.config.ConfigFactory
import franz.rest.BaseRouteTest
import org.http4s.{HttpRoutes, Status}
import zio.{Task, UIO}

import scala.util.Success

class ConfigRouteTest extends BaseRouteTest {

  "ConfigRoute.configForName" should {
    val routeUnderTest: HttpRoutes[Task] = ConfigRoute.configForName {
      case None => Task.some(ConfigFactory.parseString("foo : true"))
      case Some("notfound") => Task.none
      case Some(name) => Task.some(ConfigFactory.parseString(s"this : 'is the config for $name'"))
    }

    val GetFoo = "/config?name=foo"
    s"return the configuration for GET $GetFoo" in {
      val response = routeUnderTest.responseFor(get(GetFoo))
      response.status shouldBe Status.Ok
      response.bodyAs[Map[String, String]] shouldBe Success(Map("this" -> "'is the config for foo'"))
    }

    s"return 404 for not-found configurations" in {
      routeUnderTest.responseForOpt(get("/config/notfound")) shouldBe None
    }

    val GetDefault = "/config"
    s"return the default configuration for GET $GetDefault" in {
      val response = routeUnderTest.responseFor(get(GetDefault))
      response.status shouldBe Status.Ok
      response.bodyAs[Map[String, Boolean]] shouldBe Success(Map("foo" -> true))
    }
  }
  "ConfigRoute.saveConfig" should {
    val routeUnderTest: HttpRoutes[Task] = ConfigRoute.saveConfig {
      case ConfigService.SaveRequest("fail", msg) => UIO.left(s"BANG! --> $msg")
      case ConfigService.SaveRequest("throw", msg) => sys.error(msg)
      case ConfigService.SaveRequest(_, _) => UIO.right(())
    }

    val BadName = "/config/bad..name"
    s"return a failure for POST $BadName" in {
      val response = routeUnderTest.responseFor(post(BadName, "foo : true"))
      response.status shouldBe Status.BadRequest
      response.bodyAs[String] shouldBe Success("Invalid config name 'bad..name'")
    }
    s"return success for valid config names" in {
      val response = routeUnderTest.responseFor(post("/config/valid", "foo : true"))
      response.status shouldBe Status.Ok
      response.bodyAsString shouldBe ""
    }

    s"return a failure when the save handler throws an exception" in {
      val response = routeUnderTest.responseFor(post("/config/throw", "doesn't matter"))
      response.status shouldBe Status.InternalServerError
      response.bodyAs[String] shouldBe Success("Encountered a bug saving: 'throw': doesn't matter")
    }
    "return a failure when the save handler returns a left message" in {
      val response = routeUnderTest.responseFor(post("/config/fail", "this is the content of the failed handler"))
      response.status shouldBe Status.InternalServerError
      response.bodyAs[String] shouldBe Success("Error saving 'fail': BANG! --> this is the content of the failed handler")
    }
  }
}
