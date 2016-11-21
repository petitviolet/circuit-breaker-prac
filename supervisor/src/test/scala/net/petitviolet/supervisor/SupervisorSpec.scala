package net.petitviolet.supervisor

import java.util.concurrent.TimeoutException

import akka.pattern.ask
import net.petitviolet.supervisor.Supervisor._

import scala.concurrent.Future

class SupervisorSpec extends SpecBase("supervisor-spec") {
  import scala.concurrent.duration._
  private val runTimeout: FiniteDuration = 10.millis
  private val resetWait: FiniteDuration = 20.millis

  private[this] case object FutureException extends RuntimeException("fail future")

  def props(maxFailCount: Int = 2) = Supervisor.props[Int](maxFailCount, runTimeout, resetWait)
  def buildSupervisorRef(maxFailCount: Int = 2) = system.actorOf(props(maxFailCount))

  "`Close` supervisor" must {
    "return success future" in {
      whenReady(buildSupervisorRef() ? Execute(Future(1)), runTimeout) { result: Any =>
        result shouldBe an[Integer]
        result shouldBe 1
      }
    }

    "return success future with implicit conversion" in {
      whenReady(buildSupervisorRef() supervise Future(1), runTimeout) { result: Int =>
        result shouldBe 1
      }
    }

    "return failure future over timeout" in {
      val sleepTimeout = runTimeout + 2.millis
      val resultF: Future[Any] = buildSupervisorRef() ? Execute(Future { Thread.sleep(sleepTimeout.toMillis); 1 })
      whenReady(resultF.failed, runTimeout) { result: Throwable =>
        result shouldBe a[TimeoutException]
      }
    }

    "return failure future over timeout with implicit conversion" in {
      val sleepTimeout = runTimeout + 2.millis
      val resultF = buildSupervisorRef() supervise Future { Thread.sleep(sleepTimeout.toMillis); 1 }
      whenReady(resultF.failed, runTimeout) { result: Throwable =>
        result shouldBe a[TimeoutException]
      }
    }

    "return failed future" in {
      val resultF = buildSupervisorRef() ? Execute(Future.failed(FutureException))
      whenReady(resultF.failed, runTimeout) { result: Throwable =>
        result shouldBe FutureException
      }
    }
  }

  "`Open` supervisor" must {
    "return MessageOnOpenException" in {
      val actor = buildSupervisorRef(maxFailCount = 1)

      // make supervisor `Open`
      val resultF1 = actor ? Execute(Future.failed(FutureException))
      whenReady(resultF1.failed, runTimeout) { result: Throwable =>
        result shouldBe FutureException
      }

      // message on `Open`
      val resultF2 = actor ? Execute(Future.failed(FutureException))
      whenReady(resultF2.failed, runTimeout) { result: Throwable =>
        result shouldBe a[MessageOnOpenException]
      }
    }
  }
}
