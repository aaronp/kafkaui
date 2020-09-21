package franz.data.crud.routes

import cats.effect.Sync
import cats.implicits._
import franz.data.{CollectionName, VersionedRecord}
import franz.data.crud.InsertRecord
import franz.users.routes._
import franz.users.{PermissionPredicate, User, WebUser}
import io.circe.{Decoder, Encoder}
import org.http4s.AuthedRoutes
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl

/**
 * Wraps an authenticated route around an [[InsertRecord]] service
 */
object InsertRecordRoute {
  def single[F[_] : Sync, A: Decoder, R: Encoder](singlePath: String,
                                                  service: InsertRecord.Service[F, (User, A), R],
                                                  permissions: PermissionPredicate[F]): AuthedRoutes[WebUser, F] = {
    apply(Seq(singlePath), service, permissions, Http4sDsl[F])
  }


  def apply[F[_] : Sync, A: Decoder, R: Encoder](path: Seq[String],
                                                 service: InsertRecord.Service[F, (User, A), R],
                                                 permissions: PermissionPredicate[F], dsl: Http4sDsl[F] = Http4sDsl[F]): AuthedRoutes[WebUser, F] = {


    import dsl._

    val PostPath = join(path, dsl)

    AuthedRoutes.of {
      case authRequest@POST -> PostPath as jwtUser =>
        val requiredPermission: String = asPermission(authRequest)
        val (jwt, user) = jwtUser

        implicit val decoder = jsonOf[F, A]
        permissions.isPermitted(user, requiredPermission).flatMap {
          case true =>
            for {
              body <- authRequest.req.as[A]
              response <- service.insert((user, body))
              resp <- ok(response, jwt, dsl)
            } yield resp
          case false => setToken(notAuthorised(dsl.Unauthorized, requiredPermission), jwt)
        }
    }
  }

  def anyCollectionNoUser[F[_] : Sync, A: Decoder, R: Encoder](namespace: String,
                                                               userAgnosticService: InsertRecord.Service[F, (CollectionName, VersionedRecord[A]), R],
                                                               permissions: PermissionPredicate[F], dsl: Http4sDsl[F] = Http4sDsl[F]): AuthedRoutes[WebUser, F] = {
    val service = InsertRecord.contraMap[F, (CollectionName, VersionedRecord[A]), (User, CollectionName, VersionedRecord[A]), R](userAgnosticService) {
      case (user: User, collection, data) => (collection, data.withUser(Option(user.userId).getOrElse(user.name)))
    }
    anyCollection(namespace, service, permissions)
  }

  def anyCollection[F[_] : Sync, A: Decoder, R: Encoder](namespace: String,
                                                         service: InsertRecord.Service[F, (User, CollectionName, A), R],
                                                         permissions: PermissionPredicate[F], dsl: Http4sDsl[F] = Http4sDsl[F]): AuthedRoutes[WebUser, F] = {
    import dsl._
    val Namespace = namespace
    AuthedRoutes.of {
      case authRequest@POST -> Root / Namespace / collectionName as jwtUser =>
        val requiredPermission: String = asPermission(authRequest)
        val (jwt, user) = jwtUser

        implicit val decoder = jsonOf[F, A]
        permissions.isPermitted(user, requiredPermission).flatMap {
          case true =>
            for {
              body <- authRequest.req.as[A]
              response <- service.insert((user, collectionName, body))
              resp <- ok(response, jwt, dsl)
            } yield resp
          case false => setToken(notAuthorised(dsl.Unauthorized, requiredPermission), jwt)
        }
    }
  }
}
