package franz.data.diff

import cats.{ApplicativeError, Monad}
import cats.data.Validated._
import cats.data._
import cats.effect.Sync
import cats.implicits._
import franz.data._
import franz.data.diff.Diff._
import franz.rest.Swagger
import franz.rest.Swagger.{GetRequest, Parser, PostRequest, parserForJson}
import io.circe.Json
import io.circe.syntax._

/**
 *
 * In the 'GET' variant, all query parameters are optional:
 *
 * On its own,
 * {{{
 *   GET /diff/<collection>/<id>
 * }}}
 *
 * will return a difference between the latest and previous versions of a given record.
 * $ otherVersion - specifies a specific other version to diff against (same collection and id)
 * $ otherCollection - specifies a different collection to diff against (same version and id, so on its own this is likely nonsensical, although could represent a diff between and audit collection and a 'latest' one)
 * $ otherId - specifies a different record ID to diff against (same version and collection)
 *
 *
 * {{{
 *   GET /diff/<collection>/<id>?otherVersion=2&otherCollection=XYZ&otherId=foo# diff a records between two versions
 *   POST /diff # POST difference, perhaps between collections, ids and versions
 * }}}
 *
 */
object DiffRest {

  def client[F[_]](client: Swagger.Client[F, Json], usePost: Boolean = true)(implicit monad : Monad[F], appErr : ApplicativeError[F, Throwable]): Diff.Service[F] = {
    if (usePost) postClient(client) else getClient(client)
  }

  def getClient[F[_]](client: Swagger.Client[F, Json])(implicit monad : Monad[F], appErr : ApplicativeError[F, Throwable]): Diff.Service[F] = {
    DiffGetClient[F, Json](client, parserForJson[F, Option[Diff.Result]])
  }

  def postClient[F[_]](client: Swagger.Client[F, Json])(implicit monad : Monad[F], appErr : ApplicativeError[F, Throwable]): Diff.Service[F] = {
    DiffPostClient[F, Json](client, parserForJson[F, Option[Diff.Result]])
  }

  val Namespace = "diff"

  object QueryParams {
    type ParseResult[A] = Validated[NonEmptyChain[String], A]

    val Version = "version"

    val OtherCollection = "otherCollection"
    val OtherVersion = "otherVersion"
    val OtherId = "otherId"

    def parseDiffRequest(leftCoords: RecordCoords, queryParams: Map[String, Seq[String]]): ParseResult[Request] = {
      val versionV: ParseResult[RecordVersion] = RecordVersion.parseFromQueryParams(OtherVersion, queryParams) match {
        case Invalid(err) => err.invalidNec
        case Valid(v) => Valid(v)
      }

      (parseKey(OtherCollection, queryParams), parseKey(OtherId, queryParams), versionV).mapN {
        case (collOpt, idOpt, version) =>
          val rightCoords = RecordCoords(collOpt.getOrElse(leftCoords.collection), idOpt.getOrElse(leftCoords.id), version)
          Diff.Request(leftCoords, rightCoords)
      }
    }


    def parseKey(key: String, queryParams: Map[String, Seq[String]]): ParseResult[Option[String]] = {
      queryParams.getOrElse(key, Nil) match {
        case Seq(single) => single.some.validNec
        case Seq() => None.validNec
        case many => s"${many.size} query params were given (zero or one expected) for ${key}: ${many.mkString(",")}".invalidNec
      }
    }
  }

  def diffPost(request: Diff.Request): PostRequest = {
    PostRequest(s"/rest/$Namespace", request.asJson)
  }

  def diffGet(request: Diff.Request): GetRequest = {
    import QueryParams._
    import request._

    val queryParams = List(
      s"$Version=${leftHandSide.version.queryValue}",
      s"$OtherCollection=${rightHandSide.collection}",
      s"$OtherId=${rightHandSide.id}",
      s"$OtherVersion=${rightHandSide.version.queryValue}"
    )

    val baseUrl = s"/rest/$Namespace/${leftHandSide.collection}/${leftHandSide.id}?"
    GetRequest(queryParams.mkString(baseUrl, "&", ""))
  }

  final case class DiffGetClient[F[_] : Monad, A](client: Swagger.Client[F, A], parser: Parser[F, A, Option[Diff.Result]]) extends Diff.Service[F] {
    override def diff(request: Diff.Request): F[Option[Diff.Result]] = {
      Monad[F].flatMap(client.run(diffGet(request)))(parser.apply)
    }
  }

  final case class DiffPostClient[F[_] : Monad, A](client: Swagger.Client[F, A], parser: Parser[F, A, Option[Diff.Result]]) extends Diff.Service[F] {
    override def diff(request: Diff.Request): F[Option[Diff.Result]] = {
      Monad[F].flatMap(client.run(diffPost(request)))(x => parser(x))
    }
  }

}
