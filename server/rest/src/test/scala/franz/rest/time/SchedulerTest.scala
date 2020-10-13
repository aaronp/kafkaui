package franz.rest.time

import java.time.{LocalDateTime, ZoneOffset}

import franz.rest.time.SchedulerTest.Heartbeat.{Expired, Timeout}
import zio._
import zio.clock.Clock
import zio.console.Console
import zio.random.Random

import scala.concurrent.duration.FiniteDuration

object SchedulerTest extends zio.App {

  case class Range(min: FiniteDuration, max: FiniteDuration) {
    def isFixed = min == max

    val next: URIO[Random, Int] = if (isFixed) {
      URIO.succeed(min.toMillis.toInt)
    } else {
      zio.random.nextIntBetween(min.toMillis.toInt, max.toMillis.toInt)
    }
    val nextDuration = next.map(millis => zio.duration.Duration.fromMillis(millis.toInt))
  }

  object Range {
    def apply(fixed: FiniteDuration): Range = Range(fixed, fixed)
  }

  sealed trait Input

  case class Next(text: String) extends Input

  case object HB extends Input

  import scala.concurrent.duration._

  case class SomeState(text: String, count: Int, lastTime: LocalDateTime, lastDelay: FiniteDuration) {
    def update(in: Input): SomeState = {
      val now = LocalDateTime.now()
      val diff = (now.toInstant(ZoneOffset.UTC).toEpochMilli - lastTime.toInstant(ZoneOffset.UTC).toEpochMilli).millis


      val result = in match {
        case Next(x) => copy(text = x, count = 0, lastTime = now, lastDelay = diff)
        case HB => copy(count = count + 1, lastTime = now, lastDelay = diff)
      }

      println(
        s"""
           | = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = =
           |$this
           |given $in
           |yields
           |$result
           | = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = =
           |""".stripMargin)
      result
    }
  }

  def driveState(stateRef: Ref[SomeState], queue: Queue[_]) = {
    val poll = for {
      _ <- queue.take
      _ <- zio.console.putStrLn("On Heartbeat Timeout")
      _ <- Heartbeat.scheduleHeartbeat
    } yield ()
    poll.forever
  }

  type Heartbeat = Has[Heartbeat.Service]

  object Heartbeat {

    case object Expired

    trait Service {
      def scheduleHeartbeat(): UIO[Unit]
    }

    def scheduleHeartbeat: ZIO[Heartbeat, Nothing, Unit] = ZIO.accessM[Heartbeat](_.get.scheduleHeartbeat())

    def scheduleNext(range: Range, queue: Queue[Expired.type]) = {
      val scheduled = for {
        time <- range.nextDuration
        _ <- ZIO.sleep(time)
        _ <- queue.offer(Expired)
      } yield ()
      scheduled.onInterrupt(x => zio.console.putStrLn("HB cancelled"))
    }

    type Timeout = Fiber[Nothing, Unit]

    def forRangeAndQueue(range: Range, queue: Queue[Expired.type], hbRef: Ref[Option[Timeout]]) = ZLayer.fromFunction[Clock with Random with Console, Service] { clockAndRandom =>
      new Service {
        def enqueue = scheduleNext(range, queue).provide(clockAndRandom).fork

        override def scheduleHeartbeat(): UIO[Unit] = {
          for {
            opt <- hbRef.get
            newFiber <- opt match {
              case Some(existing) => existing.interrupt *> enqueue
              case None => enqueue
            }
            _ <- hbRef.set(Some(newFiber))
          } yield ()
        }
      }
    }
  }


  def userInput(stateRef: Ref[SomeState]) = {
    val read = for {
      _ <- Heartbeat.scheduleHeartbeat
      _ <- zio.console.putStr("Next:")
      line <- zio.console.getStrLn
      current <- stateRef.get
      _ <- zio.console.putStrLn(s"Current State is $current")
    } yield ()
    read
  }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {

    val range = Range(1.second, 3.seconds)

    val app = for {
      stateRef <- Ref.make(SomeState("", 0, LocalDateTime.now(), 0.millis))
      hbRef <- Ref.make[Option[Timeout]](None)
      queue <- Queue.bounded[Expired.type](10)
      _ = println("creating HB")
      hbLayer = Heartbeat.forRangeAndQueue(range, queue, hbRef)
      //      _ <- scheduleHeartbeat(range, queue)
      _ <- driveState(stateRef, queue).provideCustomLayer(hbLayer).fork
      _ <- userInput(stateRef).provideCustomLayer(hbLayer).forever
    } yield ()


    app.exitCode
  }
}
