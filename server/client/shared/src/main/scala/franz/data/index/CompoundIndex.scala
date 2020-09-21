package franz.data.index

import cats.effect.Sync
import cats.syntax.functor._
import cats.{Applicative, ApplicativeError, Monad}
import donovan.json.JPath
import franz.data._
import franz.data.crud.{CrudServices, CrudServicesInMemory}
import franz.rest.Swagger
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}
import franz.data.JsonAtPath._

/**
 *
 * A means to either flatten several json values into a single value, or take a single value and explode it into multiple values
 *
 * e.g. the compound index could be to concatenate two fields (firstName + lastName -> fullName) or expand a field (stem 'firstName' into aliases or soundex values)
 *
 * {{{
 *   POST /compoundindex  # create a compound index to use when writing records
 *   LIST /compoundindex  # list the compound indices
 *   DELETE /compoundindex/<id>  # remove a compound index
 * }}}
 *
 */
sealed trait CompoundIndex {

  def targetPath: List[String]

  /**
   * Update the input json document given this index
   *
   * @param input the original json
   * @return an updated json
   */
  def applyTo(input: Json): Json


  /**
   * Apply this index to the given json
   *
   * @param input
   * @tparam A
   * @return
   */
  def apply[A: Encoder](input: A): Json = applyTo(input.asJson)

}

/**
 *
 * The case where we're collapsing several values into one
 *
 * @param fromPaths         a list of the json paths (e.g. ["address", "line1"]
 * @param targetPath        the json path where the result should be saved (e.g. Seq("address", "oneline"))
 * @param compoundSeparator the character to use when concatenating the fields
 */
case class MergeValues(fromPaths: Seq[MergeValues.SelectPath], targetPath: List[String], compoundSeparator: String = ";") extends CompoundIndex {
  override def toString = fromPaths.mkString(s"Merge ${fromPaths.size} paths: [\n\t", ",\n\t", s"] \ninto ${targetPath.mkString(".")} with separator '$compoundSeparator'")

  override def applyTo(input: Json): Json = {
    val compountStr: Array[String] = fromPaths.foldLeft(Array.empty[String]) {
      case (concat, select) =>
        val opt = JPath.forParts(select.path).selectValue(input).map(select.prepare)
        opt.fold(concat)(_ +: concat)
    }

    if (compountStr.isEmpty) {
      input
    } else {
      val indexJson = jsonForCompoundValues(compountStr.reverse)
      donovan.json.deepMergeWithArrayConcat(input, indexJson)
    }
  }

  /**
   * @param value the assumed computation of the compound index as a string
   * @return the value as a json nested within the target path
   */
  def valueAsJson(value: String) = CompoundIndex.nestJson(targetPath, Json.fromString(value))

  def jsonForCompoundValues(selectedValues: Iterable[String]) = valueAsJson(selectedValues.mkString(compoundSeparator))
}

object MergeValues {

  def of(singlePath: List[String], targetPath: List[String], compoundSeparator: String = ";", stempOps: Seq[StemOp] = Nil) = {
    new MergeValues(Seq(SelectPath(singlePath, stempOps)), targetPath, compoundSeparator)
  }

  /**
   * @param path     the json path
   * @param stempOps the operations to apply to each input strings
   */
  case class SelectPath(path: List[String], stempOps: Seq[StemOp] = Nil) {
    override def toString = {
      val opsStr = if (stempOps.isEmpty) "" else {
        stempOps.mkString(s" and then: [", ",", "]")
      }
      path.mkString("select:", ".", opsStr)
    }

    def prepare(input: Json): String = StemOp.prepareString(stempOps, asString(input))
  }

  object SelectPath {
    def apply(path: List[String], first: StemOp, theRest: StemOp*): SelectPath = {
      new SelectPath(path, first +: theRest.toList)
    }

    implicit val encoder = io.circe.generic.semiauto.deriveEncoder[SelectPath]
    implicit val decoder = io.circe.generic.semiauto.deriveDecoder[SelectPath]
  }

  implicit val encoder = io.circe.generic.semiauto.deriveEncoder[MergeValues]
  implicit val decoder = io.circe.generic.semiauto.deriveDecoder[MergeValues]
}

/**
 * A means to take a single value and convert it into multiple values.
 *
 * for example, we could split strings on spaces, or derive aliases for particular values ("Johnathan" -> ["Jon", "John", "Jonathan", "Johnathan"])
 *
 * @param fromPath
 * @param targetPath the target json path which will be an array
 */
case class ExpandValues(fromPath: List[String], targetPath: List[String], splitOperation: SplitOp) extends CompoundIndex {
  override def toString = fromPath.mkString(s"Expand ", ".", s" into ${targetPath.mkString(".")} using $splitOperation")

  override def applyTo(input: Json): Json = {
    val opt = JPath.forParts(fromPath).selectValue(input).map { json: Json =>
      val values = splitOperation(asString(json))
      val arrayJson = Json.fromValues(values.map(Json.fromString))
      val indexJson = CompoundIndex.nestJson(targetPath, arrayJson)
      donovan.json.deepMergeWithArrayConcat(input, indexJson)
    }

    opt.getOrElse(input)
  }
}

object ExpandValues {
  implicit val encoder = io.circe.generic.semiauto.deriveEncoder[ExpandValues]
  implicit val decoder = io.circe.generic.semiauto.deriveDecoder[ExpandValues]
}

object CompoundIndex {

  val Namespace = "compoundindices"


  /**
   * split a string value into an array
   *
   * @param fromPath the path to the value to expand
   * @param targetPath where we should expand the value to (the path to the expanded values)
   * @param splitOperation how to 'expand' (split) the string
   * @return
   */
  def expand(fromPath: List[String], targetPath: List[String], splitOperation: SplitOp = SplitOp.SplitString("\\w")): CompoundIndex = {
    ExpandValues(fromPath, targetPath, splitOperation)
  }

  def merge(fromPaths: Seq[MergeValues.SelectPath], targetPath: List[String], compoundSeparator: String = ";"): CompoundIndex = {
    MergeValues(fromPaths, targetPath, compoundSeparator)
  }

  /**
   * Apply the given indices to the 'original' json data
   *
   * @param originalRecord
   * @param compoundIndices
   * @return
   */
  def apply(originalRecord: VersionedJson, compoundIndices: Seq[CompoundIndex]): VersionedJson = {
    if (compoundIndices.isEmpty) {
      originalRecord
    } else {
      val newData = compoundIndices.foldLeft(originalRecord.data) {
        case (jsonAccum, index) => index.applyTo(jsonAccum)
      }
      originalRecord.withData(newData)
    }
  }

  def nestJson(targetPath: List[String], value: Json): Json = {
    targetPath match {
      case Nil => value
      case head :: tail => Json.obj(head -> nestJson(tail, value))
    }
  }


  /**
   * Puts some convenience functions/guidance around our [[CompoundIndex]] CRUD
   *
   * @param crudClient the underlying client
   * @tparam F
   */
  case class DSL[F[_] : Monad](crudClient: CrudServices[F, Seq[CompoundIndex]]) {

    def listIndices(collection: CollectionName): F[Seq[CompoundIndex]] = {
      Monad[F].map(indicesForCollection(collection)) {
        case None => Seq()
        case Some(list) => list.data
      }
    }

    def indicesForCollection(collection: CollectionName): F[Option[VersionedRecord[Seq[CompoundIndex]]]] = {
      crudClient.readService.read(RecordCoords(Namespace, collection))
    }

    def addIndicesToCollection(collection: CollectionName, newIndices: Seq[CompoundIndex]): F[VersionedResponse[Seq[CompoundIndex]]] = {
      import cats.syntax.flatMap._
      indicesForCollection(collection).flatMap {
        case None =>
          createIndicesForCollection(collection, newIndices)
        case Some(latest) =>
          updateIndices(latest.withData((newIndices ++ latest.data).distinct).incVersion)
      }
    }

    def addIndexToCollection(collection: CollectionName, index: CompoundIndex): F[VersionedResponse[Seq[CompoundIndex]]] = {
      addIndicesToCollection(collection, List(index))
    }
    def replaceIndexInCollection(collection : CollectionName, oldIndex : CompoundIndex, newIndex: CompoundIndex): F[VersionedResponse[Seq[CompoundIndex]]] = {
      import cats.syntax.flatMap._
      indicesForCollection(collection).flatMap {
        case None =>
          createIndicesForCollection(collection, List(newIndex))
        case Some(latest) =>
          val updated = latest.data.diff(List(oldIndex)) :+ newIndex
          updateIndices(latest.withData(updated).incVersion)
      }
    }

    def removeIndexFromCollection(collection: CollectionName, index: CompoundIndex): F[Option[VersionedResponse[Seq[CompoundIndex]]]] = {
      removeIndicesFromCollection(collection, List(index))
    }

    def removeIndicesFromCollection(collection: CollectionName, toRemove: Seq[CompoundIndex]): F[Option[VersionedResponse[Seq[CompoundIndex]]]] = {
      import cats.syntax.flatMap._
      indicesForCollection(collection).flatMap {
        case None => Applicative[F].point(None)
        case Some(latest) =>
          import cats.syntax.option._
          updateIndices(latest.withData((latest.data diff toRemove)).incVersion).map(_.some)
      }
    }

    def createIndicesForCollection(collection: CollectionName, indices: Seq[CompoundIndex]): F[VersionedResponse[Seq[CompoundIndex]]] = {
      import VersionedRecord.syntax._
      crudClient.insert(indices.versionedRecord(id = collection))
    }

    def updateIndices(indices: VersionedRecord[Seq[CompoundIndex]]): F[VersionedResponse[Seq[CompoundIndex]]] = {
      crudClient.insert(indices)
    }
  }

  def client[F[_]](restClient: Swagger.Client[F, Json])(implicit monad: Monad[F], appErr: ApplicativeError[F, Throwable]): DSL[F] = {
    DSL(CrudServices.client[F, Seq[CompoundIndex]](Namespace, restClient))
  }


  def inMemory[F[_] : Sync]: CrudServices[F, Seq[CompoundIndex]] = inMemorySetup[F].services

  def inMemorySetup[F[_] : Sync] = CrudServicesInMemory[F, Seq[CompoundIndex]](true)

  implicit val encoder: Encoder[CompoundIndex] = Encoder.instance[CompoundIndex] {
    case msg: ExpandValues => msg.asJson
    case msg: MergeValues => msg.asJson
  }

  implicit val decoder: Decoder[CompoundIndex] = {
    ExpandValues.decoder.widen[CompoundIndex]
      .or(MergeValues.decoder.widen[CompoundIndex])
  }
}
