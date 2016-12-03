package net.petitviolet.supervisor

import java.util.concurrent.TimeoutException

import akka.actor.Props
import akka.actor.Status._
import akka.pattern._
import akka.testkit._
import net.petitviolet.supervisor.Supervisor._
import org.scalatest.Assertion

import scala.concurrent.Future

class SupervisorSpec extends SpecBase("supervisor-spec") {
  import scala.concurrent.duration._
  private val runTimeout: FiniteDuration = 10.milliseconds.dilated
  private val resetWait: FiniteDuration = 20.milliseconds.dilated

  private[this] case object FutureException extends RuntimeException("fail future")

  private def props(maxFailCount: Int = 2): Props = Supervisor.props[Int](maxFailCount, runTimeout, resetWait)

  private def buildSupervisorRef(maxFailCount: Int = 2): TestActorRef[Supervisor[_]] =
    TestActorRef.apply[Supervisor[_]](props(maxFailCount))

  private def expectTimeoutException: PartialFunction[Any, Assertion] = {
    case Failure(t) if t.isInstanceOf[TimeoutException] => succeed
    case unknown =>
      info(s"unknown: $unknown")
      fail
  }

  "`Close` supervisor" must {
    "return success value" when {
      "given success future" in {
        val supervisor = buildSupervisorRef()
        supervisor.receive(Execute(Future(1)), testActor)
        expectMsg(Success(1))
      }

      "given success future with implicit conversion" in {
        val supervisor = buildSupervisorRef()
        (supervisor supervise Future(1)).pipeTo(testActor)
        expectMsg(1)
      }
    }

    "return failure" when {
      "given future timeouts" in {
        val supervisor = buildSupervisorRef()
        supervisor.receive(Execute(Future { Thread.sleep((runTimeout * 2).toMillis); 1 }), testActor)
        expectMsgPF()(expectTimeoutException)
      }

      "given future timeouts with implicit conversion" in {
        val supervisor = buildSupervisorRef()
        (supervisor supervise Future { Thread.sleep((runTimeout * 2).toMillis); 1 }).pipeTo(testActor)
        expectMsgPF()(expectTimeoutException)
      }

      "given failed future" in {
        val supervisor = buildSupervisorRef()
        supervisor.receive(Execute(Future.failed(FutureException)), testActor)
        expectMsg(Failure(FutureException))
      }
    }

    "return fallback value" when {
      val fallback = 100L

      "failure with failing future" in {
        val supervisor = buildSupervisorRef()
        supervisor.receive(ExecuteWithFallback(Future { Thread.sleep((runTimeout * 2).toMillis); 1 }, fallback), testActor)
        expectMsg(Success(fallback))
      }

      "given future timeouts with implicit conversion" in {
        val supervisor = buildSupervisorRef()
        (supervisor supervise (Future { Thread.sleep((runTimeout * 2).toMillis); 1 }, fallback)).pipeTo(testActor)
        expectMsg(fallback)
      }

      "given failed future" in {
        val supervisor = buildSupervisorRef()
        supervisor.receive(ExecuteWithFallback(Future.failed(FutureException), fallback), testActor)
        expectMsg(Success(fallback))
      }
    }
  }

  "`Close` supervisor's state" must {
    "no change" when {
      "failing less than `maxFailCount`" in {
        val supervisor = buildSupervisorRef(maxFailCount = 2)

        supervisor.receive(Execute(Future.failed(FutureException)), testActor)
        expectMsg(Failure(FutureException))

        supervisor.receive(Execute(Future(1)), testActor)
        expectMsg(Success(1))
      }
    }

    "gets `Open`" when {
      "fails over `maxFailCount`" in {
        val supervisor = buildSupervisorRef(maxFailCount = 1)

        // make supervisor `Open`
        supervisor.receive(Execute(Future.failed(FutureException)), testActor)
        expectMsg(Failure(FutureException))

        // message on `Open`
        supervisor.receive(Execute(Future.failed(FutureException)), testActor)
        expectMsg(Failure(MessageOnOpenException))
      }
    }
  }

  "`Open` supervisor" must {
    s"get $MessageOnOpenException result" when {
      s"try to execute future" in {
        val supervisor = buildSupervisorRef(maxFailCount = 1)
        supervisor.underlyingActor.becomeOpen()

        // message on `Open`
        supervisor.receive(Execute(Future(1)), testActor)
        expectMsg(Failure(MessageOnOpenException))
      }
    }
  }

  "`Open` supervisor state" must {
    "no change" when {
      "before pass `resetTimeout`" in {
        val supervisor = buildSupervisorRef(maxFailCount = 1)
        supervisor.underlyingActor.becomeOpen()

        // wait less than `Half-Open`
        Thread.sleep((resetWait / 2).toMillis)
        // still `Open`
        supervisor.underlyingActor.state shouldBe Open
      }
    }

    "gets `Half-Open`" when {
      "pass `resetWait`" in {
        val supervisor = buildSupervisorRef(maxFailCount = 1)
        supervisor.underlyingActor.becomeOpen()
        supervisor.underlyingActor.state shouldBe Open

        // wait to get `Half-Open`
        Thread.sleep(resetWait.toMillis * 2) // TODO something wrong... providing `resetWait` will fail...
        supervisor.underlyingActor.state shouldBe HalfOpen
      }
    }
  }

  "`Half-Open` supervisor" must {
    "success executing success future" in {
      val supervisor = buildSupervisorRef(maxFailCount = 1)
      supervisor.underlyingActor.becomeHalfOpen()

      // success future on `Half-Open`
      supervisor.receive(Execute(Future(1)), testActor)
      expectMsg(Success(1))

      supervisor.underlyingActor.state shouldBe Close
    }

    "fail executing failure future" in {
      val supervisor = buildSupervisorRef(maxFailCount = 1)
      supervisor.underlyingActor.becomeHalfOpen()

      // fail failure future on `Half-Open`
      supervisor.receive(Execute(Future.failed(FutureException)), testActor)
      expectMsg(Failure(FutureException))

      supervisor.underlyingActor.state shouldBe Open
    }

  }
}
