package franz.data.query

import io.circe.generic.auto._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class NamedSchemaTest extends AnyWordSpec with Matchers {

  import RichNamedSchema._

  "NamedSchema.md5" should {
    "produce different keys for different json" in {
      val x = NamedSchemaTest.Data(123, "foo", true, None)
      val y = NamedSchemaTest.Data(-3, "bar", false, None)
      val z = NamedSchemaTest.Data(-3, "bar", false, Some(x))
      val schema1 = NamedSchema(x)
      val schema2 = NamedSchema(y)
      val schema3 = NamedSchema(z)
      schema1 shouldBe schema2
      schema1 should not be schema3
      schema1.md5 shouldBe schema2.md5
      schema1.md5 should not be schema3.md5
    }
  }
}

object NamedSchemaTest {

  case class Data(x: Int, str: String, ok: Boolean, child: Option[Data])

}
