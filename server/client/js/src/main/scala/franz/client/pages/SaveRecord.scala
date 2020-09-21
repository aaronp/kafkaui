package franz.client.pages

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyChain, Validated}
import franz.client.js.{MessageAlerts, appClientFuture}
import franz.data.{CollectionName, VersionedJson, VersionedJsonResponse}

import scala.concurrent.ExecutionContext.Implicits._
import scala.util.{Failure, Success}
import cats.syntax.option._
import franz.client.bootstrap.Component

import scala.concurrent.Future

/**
 * Logic snippet for saving records
 */
object SaveRecord {

  def apply(nav: Nav, //
            validatedRecord: Validated[NonEmptyChain[Component.Error], (CollectionName, VersionedJson)], //
            errors: MessageAlerts //
           ): Option[Future[VersionedJsonResponse]] = {
    validatedRecord match {
      case Valid((collection, data)) =>
        val future = appClientFuture.crudClient.insert(collection, data)
          future.onComplete {
          case Failure(err) =>
            errors.pushMessage(s"Saving ${data.id} to $collection failed with $err")
          case Success(result) =>
            result.toEither match {
              case Left(err) =>
                errors.pushMessage(s"Saving ${data.id} to $collection failed with ${err.getMessage}")
              case Right(saved) =>
                nav.move.toRecord(collection, saved)
            }
        }
        future.some
      case Invalid(errorMsgs) =>
        errorMsgs.iterator.foreach { msg: Component.Error =>
          errors.pushMessage(msg.message)
        }
        none
    }
  }
}
