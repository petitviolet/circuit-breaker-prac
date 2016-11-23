package net.petitviolet.supervisor

import java.util.concurrent.{ Executors, ForkJoinPool }

import akka.actor.ActorSystem
import akka.testkit._
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import org.scalatest.time._
import org.scalatest.{ BeforeAndAfterAll, AsyncWordSpecLike, Matchers, WordSpecLike }

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.implicitConversions

object TestConfig {
  import java.io.File
  val config = ConfigFactory.parseFile(new File("supervisor/src/test/resources/application-test.conf"))
  println(s"config: $config")
}

class SpecBase(name: String) extends TestKit(ActorSystem(name, TestConfig.config))
  with WordSpecLike with Matchers with ScalaFutures with BeforeAndAfterAll {
  import org.scalatest.concurrent.PatienceConfiguration.{ Timeout => TestTimeout }

  implicit val timeout: akka.util.Timeout = Timeout(1.second.dilated)
  implicit def convertTimeout(duration: Duration): TestTimeout =
    TestTimeout.apply(Span.convertDurationToSpan(duration))

  implicit def ec: ExecutionContext = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())

  override protected def afterAll(): Unit = {
    super.afterAll()
    system.terminate()
  }
}
