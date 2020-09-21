package franz.db

import java.util.UUID

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import dockerenv.BaseMongoSpec
import mongo4m.{LowPriorityMongoImplicits, MongoConnect}
import monix.execution.Scheduler
import monix.execution.Scheduler.Implicits.global
import org.mongodb.scala.{Document, MongoClient, MongoCollection, MongoDatabase}

import scala.concurrent.duration._
import scala.util.Success

abstract class BaseGleichDBTest extends BaseMongoSpec with LowPriorityMongoImplicits with StrictLogging {

  def nextId(): String = UUID.randomUUID().toString

  def nextDb(): String = s"DB${nextId().filter(_.isLetter)}"

  override def testTimeout = 15.seconds

  lazy val rootConfig = ConfigFactory.load()

  lazy val connect = MongoConnect(rootConfig)

  def nextCollectionName(id: String = nextId()) = s"${getClass.getSimpleName}${id}".filter(_.isLetterOrDigit)

  def withCollection(test: (MongoCollection[Document], Scheduler) => Unit): Unit = {
    connect.use { db =>
      val usersCollectionName = s"test-${System.currentTimeMillis}"
      val settings = connect.settingsForCollection(usersCollectionName, "users")

      val coll: MongoCollection[Document] = settings.ensureCreated(db).runToFuture.futureValue
      test(coll, global)

    }
  }

  def newClient(): MongoClient = MongoConnect(rootConfig).client

  private var mongoClientCreated = false
  lazy val mongoClient: MongoClient = {
    mongoClientCreated = true
    newClient()
  }

  def mongoDb: MongoDatabase = mongoClient.getDatabase("test-db")

  def createUser() = {
    val listOutput = eventually {
      val Success((0, output)) = dockerHandle.runInScriptDir("mongo.sh", "listUsers.js")
      output
    }

    if (!listOutput.contains("serviceUser")) {
      val createOutput = eventually {
        val Success((0, output)) = dockerHandle.runInScriptDir("mongo.sh", "createUser.js")
        output
      }
      createOutput should include("serviceUser")
    }
  }

  override def afterAll(): Unit = {
    import scala.jdk.CollectionConverters._

    if (mongoClientCreated) {
      mongoClient.close()
    }

    val threads = Thread.getAllStackTraces.asScala.collect {
      case (thread, stack) if stack.exists(_.getClassName.contains("mongodb")) => thread
    }
    if (threads.nonEmpty) {
      logger.error(s"""MongoClient.close() ... doesn't. Interrupting ${threads.size} threads""".stripMargin)
      threads.foreach(_.stop())
    }

    super.afterAll()
  }
}
