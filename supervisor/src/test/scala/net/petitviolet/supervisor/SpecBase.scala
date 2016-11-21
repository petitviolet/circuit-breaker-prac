package net.petitviolet.supervisor

import java.util.concurrent.ForkJoinPool

import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.util.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time._
import org.scalatest.{ Matchers, WordSpecLike }

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.implicitConversions

class SpecBase(name: String) extends TestKit(ActorSystem(name))
  with WordSpecLike with Matchers with ScalaFutures {
  import org.scalatest.concurrent.PatienceConfiguration.{ Timeout => TestTimeout }

  implicit val timeout: akka.util.Timeout = Timeout(1.second)
  implicit def convertTimeout(duration: Duration): TestTimeout =
    TestTimeout.apply(Span.convertDurationToSpan(duration))
  implicit val ec = ExecutionContext.fromExecutor(new ForkJoinPool(1))
}
