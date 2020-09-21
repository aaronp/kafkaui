import zio._

object Playground extends zio.App {

  class MyService extends AutoCloseable {
    println("creating")

    override def close(): Unit = println("closing")
  }

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = {
    val myLayer: ZLayer[Any, Throwable, Has[MyService]] = ZLayer.fromManaged(managedService)
    program.provideLayer(myLayer).orDie as 1
  }

  def managedService: Managed[Throwable, MyService] = Managed.makeEffect(new MyService())(_.close())

  val myProgram1: ZIO[Has[MyService], Throwable, Int] = IO.succeed(1)
  val myProgram2: ZIO[Has[MyService], Throwable, Int] = IO.succeed(2)

  def program: ZIO[Has[MyService], Throwable, String] = ZIO.access[Has[MyService]](_.get).flatMap(useService)

  def useService(svc: MyService) = {
    println("useService(...)")
    for {
      _ <- myProgram1.provide(Has(svc))
      _ = println("running 2")
      _ <- myProgram2.provide(Has(svc))
      _ = println("done...")
    } yield "foo"
  }
}
