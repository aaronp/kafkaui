package franz.firestore

import franz.data.VersionedRecord.syntax._
import franz.data.{CollectionName, VersionedJsonResponse, VersionedRecord}
import io.circe.syntax._
import io.circe.{Codec, Decoder, Encoder, Json}
import zio.ZLayer
import zio.test.Assertion._
import zio.test._

// run with:
//GOOGLE_APPLICATION_CREDENTIALS=~/.config/gcloud/legacy_credentials/ud3mygcpacc0unt@gmail.com/adc.json
object FSInsertTest extends DefaultRunnableSpec {

  def spec = suite("FSInsert")(
    testM("not overwrite existing versions") {
      implicit val layer: ZLayer[Any, Throwable, FSEnv] = FSEnv.live
      val collectionName = s"collection-${getClass.getSimpleName}${System.currentTimeMillis()}"
      val drop = FSDropCollection(collectionName).provideLayer(layer).either.unit
      for {
        (record1, _, result, result2) <- insertTwo(collectionName).ensuring(drop)
      } yield {
        assert(result.isSuccess)(equalTo(true)) &&
          assert(result2.isSuccess)(equalTo(false)) &&
          assert(result.toTry.get)(equalTo(record1))
      }
    }
  )

  def insertTwo(collectionName: CollectionName)(implicit layer: ZLayer[Any, Throwable, FSEnv]) = {
    val insertService = FSInsert()
    val record1: VersionedRecord[Json] = Data("foo", Address("street", "WI")).asJson.versionedRecord(id = "first", version = 1)
    val record2: VersionedRecord[Json] = Data("foo", Address("bang", "doesn't matter")).asJson.versionedRecord(id = "first", version = 1)

    for {
      result: VersionedJsonResponse <- insertService.insert(collectionName, record1).provideLayer(layer)
      result2: VersionedJsonResponse <- insertService.insert(collectionName, record2).provideLayer(layer)
    } yield (record1, record2, result, result2)
  }

  case class Address(line1: String, state: String)

  object Address {
    val codec = io.circe.generic.semiauto.deriveCodec[Address]
    implicit val encoder: Encoder[Address] = codec
    implicit val decoder: Decoder[Address] = codec
  }

  case class Data(name: String, address: Address)

  object Data {
    val codec: Codec.AsObject[Data] = io.circe.generic.semiauto.deriveCodec[Data]
    implicit val encoder: Encoder[Data] = codec
    implicit val decoder: Decoder[Data] = codec
  }

}
