package franz.ui.routes

import java.util.UUID

import cats.effect.IO
import com.typesafe.config.ConfigFactory
import eie.io._
import franz.rest.BaseRouteTest
import org.http4s.implicits._
import org.http4s.{Request, Status}

import scala.concurrent.ExecutionContext


class StaticFileRoutesTest extends BaseRouteTest {

  "StaticFileRoutes" should {
    "serve up static files from the specified directories" in {

      s"./target/${getClass.getSimpleName}/${UUID.randomUUID()}".asPath.mkDirs().deleteAfter { tmpDir =>

        val dangerous = tmpDir.resolve("dangerous.txt").text = "dir traversal should not be allowed"

        val htmlDir1 = tmpDir.resolve("html1")
        htmlDir1.resolve("file1.html").text = "file one"
        htmlDir1.resolve("login.html").text = "the login page"
        val htmlDir2 = tmpDir.resolve("html2")
        htmlDir2.resolve("file2.html").text = "file two"
        val css1 = tmpDir.resolve("css1")
        css1.resolve("css1.css").text = "css one"
        val css2 = tmpDir.resolve("css2")
        css2.resolve("css2.css").text = "css two"
        val js1 = tmpDir.resolve("js1")
        js1.resolve("js1.js").text = "js one"
        val js2 = tmpDir.resolve("js2")
        js2.resolve("js2.js").text = "js two"

        val config = ConfigFactory.parseString(
          s"""
             |franz.www {
             |  htmlDir: [$htmlDir1,$htmlDir2]
             |  jsDir: [$js1,$js2]
             |  cssDir: [$css1,$css2]
             |}
             |""".stripMargin).withFallback(ConfigFactory.load()).resolve

        implicit val cs = IO.contextShift(ExecutionContext.Implicits.global)
        val staticFiles = StaticFileRoutes(config)
        val underTest = staticFiles.routes[IO]()

        responseFor(underTest, Request(uri = uri"/css/dave.css")).status shouldBe Status.NotFound
        responseFor(underTest, Request(uri = uri"/js/dave.css")).status shouldBe Status.NotFound

        responseFor(underTest, Request(uri = uri"/css/css1.css")).body shouldBe "css one"
        responseFor(underTest, Request(uri = uri"/css/css2.css")).body shouldBe "css two"
        responseFor(underTest, Request(uri = uri"/js/js1.js")).body shouldBe "js one"
        responseFor(underTest, Request(uri = uri"/js/js2.js")).body shouldBe "js two"
        responseFor(underTest, Request(uri = uri"/file1.html")).body shouldBe "file one"
        responseFor(underTest, Request(uri = uri"/file2.html")).body shouldBe "file two"
        responseFor(underTest, Request(uri = uri"/")).body shouldBe "the login page"
        responseFor(underTest, Request(uri = uri"")).body shouldBe "the login page"

        intercept[Exception] {
          responseFor(underTest, Request(uri = uri"/../dangerous.txt"))
        }
      }
    }
  }
}
