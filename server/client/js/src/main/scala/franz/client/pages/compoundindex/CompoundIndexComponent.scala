package franz.client.pages.compoundindex

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyChain, Validated}
import franz.client.bootstrap._
import franz.client.js.{FutureImplicits, _}
import franz.data.index.{CompoundIndex, ExpandValues, MergeValues}
import io.circe.Json
import io.circe.parser.decode
import io.circe.syntax._
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable
import scalatags.JsDom.all.{div, id, _}

import scala.util.{Failure, Success, Try}

/**
 * An input for CRUD of [[CompoundIndex]]es
 */
object CompoundIndexComponent {

  type IndexEvent = ListItem.ListItemEvent[CompoundIndex]

  /**
   * A little trial area where people can test out some input json and see what it does
   */
  object check extends FutureImplicits {
    def apply(validatedIndex: => Validated[NonEmptyChain[Component.Error], CompoundIndex]) = {
      val testResult = div().render

      val testInput = TextAreaInput(3)
      testInput.render.value =
        """{
          |  "example" : "first, second, third"
          |}""".stripMargin

      def validatedToString(formResult: Validated[NonEmptyChain[Component.Error], CompoundIndex]): String = {
        formResult match {
          case Invalid(errors) =>
            val errList = errors.iterator.toList
            errList.mkString(s"${errList.size} form errors: \n", "\n", "\n")
          case Valid(idx) =>
            decode[Json](testInput.render.value) match {
              case Left(err) => s"Error parsing test json: $err"
              case Right(inputJson) =>
                val outputJson = idx.applyTo(inputJson)
                outputJson.spaces2
            }
        }
      }

      val testButton = button(`class` := "btn btn-secondary")("Check Index").render
      testButton.onclick = e => {
        e.cancel()
        val resultJson = validatedToString(validatedIndex)
        testResult.render.innerHTML = ""
        testResult.render.appendChild(pre(resultJson).render)
      }

      div(
        div(`class` := "row")(div(`class` := "col")(p("Example Json Input:"), testInput.render)),
        div(`class` := "row")(testButton),
        div(`class` := "row")(testResult)
      ).render
    }
  }

  /**
   * Component for adding a single index, which can be either a merge or expand index
   */
  case class SingleIndex() extends Component.Delegate {

    private val containerId = uniqueId()
    private val stack = NavStack(containerId)
    private val expand = ExpandValuesComponent(stack)
    private val expandWrapped = {
      stack.push("Compound Index")(expand)
      expand.wrap { inner =>
        div(
          stack.render(),
          div(`class` := "row")(
            div(`class` := "col", id := containerId)(inner)
          ).render,
        ).render
      }
    }

    private val mergeValues = MergeValuesComponent()

    def reset(): Unit = {
      mergeValues.update(Json.Null)
      expand.update(Json.Null)
    }

    private val tabs: ChoicePanel.SelectChoiceComponent = ChoicePanel.horizontal(
      "Merge" -> mergeValues,
      "Expand" -> expandWrapped
    )

    def validatedIndex(): Validated[NonEmptyChain[Component.Error], CompoundIndex] = {
      tabs.currentLabel() match {
        case "Merge" => mergeValues.validatedIndex()
        case "Expand" => expand.validatedIndex()
      }
    }

    private val checkDiv = check {
      validatedIndex()
    }

    underlying = tabs.wrap { inner =>
      div(
        inner,
        checkDiv
      ).render
    }
  }

  /**
   * A component for listing our [[CompoundIndex]]es
   */
  case class ListIndices(refreshTheUIOnUpdate : Boolean = false) extends Component.Delegate {
    def renderCell(cell: Json): String = JsonAsString(cell)

    val createIndexComponent = SingleIndex()

    /**
     * How should we display this json in the cell
     *
     * @param panelJson
     * @return
     */
    private def fmtIndexCell(panelJson: Json) = {
      indexForChoiceJson(panelJson) match {
        case Failure(_) => JsonAsString(panelJson)
        case Success(index) =>
          // we're lazy here - just toString it
          index.toString
      }
    }

    val indicesComponent = ListItem(createIndexComponent,
      List(
        ("compound index", fmtIndexCell)
      ),
      "Compound Indices",
      addLabel = "Add Index",
      updateLabel = "Update Index",
      addButtonClass = "btn btn-success",
      autoAdd = refreshTheUIOnUpdate
    )

    /**
     * We use a ChoicePanel component whose elements need to be nsted in
     *
     * @param compoundIndices
     */
    def updateIndices(compoundIndices: Seq[CompoundIndex]) = {
      val jsonSeq = compoundIndices.map {
        case value : MergeValues => ChoicePanel.withIndex(value.asJson, 0)
        case value : ExpandValues => ChoicePanel.withIndex(value.asJson, 1)
      }
      indicesComponent.update(Json.arr(jsonSeq:_*))
    }

    private def indexForChoiceJson(choiceJson: Json) = {
      ChoicePanel.dataForJsonTry(choiceJson).as[CompoundIndex].toTry
    }

    private def asIndexObservable(choiceJson: Json) = {
      Observable(indexForChoiceJson(choiceJson))
    }

    /**
     * The raw events coming from the index control happen to be the panel component json -- that is to say,
     * the [[ChoicePanel]] json which is nested under an 'index' and 'panelData' json object
     *
     * @return events with just the compound index son
     */
    def indexEvents: Observable[Try[IndexEvent]] = indicesComponent.events.flatMap {
      case ListItem.AddEvent(choicePanelJson) =>
        asIndexObservable(choicePanelJson).map(_.map(ListItem.AddEvent.apply))
      case ListItem.RemoveEvent(choicePanelJson) =>
        asIndexObservable(choicePanelJson).map(_.map(ListItem.RemoveEvent.apply))
      case ListItem.UpdateEvent(i, beforeJson, afterJson) =>
        val updateTry = for {
          before <- indexForChoiceJson(beforeJson)
          after <- indexForChoiceJson(afterJson)
        } yield {
          ListItem.UpdateEvent(i, before, after)
        }
        Observable(updateTry)
    }

    /**
     * Add an event handler for the CRUD index events
     *
     * @param handler
     * @return
     */
    def onEvent(handler: PartialFunction[IndexEvent, Unit]) = {
      indexEvents.foreach {
        case Failure(err) =>
          sys.error(s"BUG: component control error: $err")
        case Success(e) =>
          if (handler.isDefinedAt(e)) {
            handler(e)
          }
      }
    }

    // add our own listener which will refresh the UI controls when the user adds summat
    onEvent {
      case ListItem.AddEvent(_) => createIndexComponent.reset()
    }

    underlying = indicesComponent
  }

}
