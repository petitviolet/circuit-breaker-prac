package net.petitviolet.supervisor

//package supervisor

import java.util.concurrent.{ ForkJoinPool, TimeUnit }

import akka.actor.SupervisorStrategy.Stop
import akka.actor._
import akka.util.Timeout
import com.typesafe.config.Config
import net.petitviolet.supervisor.ExecutorActor._
import net.petitviolet.supervisor.Supervisor._

import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.concurrent.{ ExecutionContextExecutor, ExecutionContext, Future }
import scala.language.implicitConversions
import scala.reflect.ClassTag

private sealed trait State
private case object Close extends State
private case object HalfOpen extends State
private case object Open extends State

sealed trait ExecuteMessage[T] {
  val run: Future[T]
}

case class Execute[T](run: Future[T]) extends ExecuteMessage[T]

case class ExecuteWithFallback[T](run: Future[T], fallback: T) extends ExecuteMessage[T]

object Supervisor {
  case class MessageOnOpenException private[supervisor] (msg: String) extends RuntimeException(msg)

  def props[T](config: Config): Props =
    Props(classOf[Supervisor[T]], maxFailCount(config), runTimeout(config), resetWait(config))

  def props[T](maxFailCount: Int, runTimeout: FiniteDuration, resetWait: FiniteDuration): Props =
    Props(classOf[Supervisor[T]], maxFailCount, runTimeout, resetWait)

  private def maxFailCount(config: Config): Int = config.getInt("max-fail-count")
  private def runTimeout(config: Config): FiniteDuration = Duration(config.getLong("run-timeout"), TimeUnit.MILLISECONDS)
  private def resetWait(config: Config): FiniteDuration = Duration(config.getLong("reset-wait"), TimeUnit.MILLISECONDS)

  private[supervisor] case object BecomeHalfOpen

  implicit class SupervisorActor(val actorRef: ActorRef) extends AnyVal {
    import akka.pattern.ask
    import scala.concurrent.duration._
    implicit def timeout: Timeout = Timeout(21474835.seconds) // maximum timeout for default

    def supervise[T](future: Future[T])(implicit ec: ExecutionContext, classTag: ClassTag[T]): Future[T] = {
      (actorRef ? Execute(future)).mapTo[T]
    }

    def supervise[T](future: Future[T], sender: ActorRef)(implicit ec: ExecutionContext, classTag: ClassTag[T]): Future[T] = {
      actorRef.ask(Execute(future))(timeout, sender).mapTo[T]
    }
  }
}

final class Supervisor[T] private (maxFailCount: Int,
                                   runTimeout: FiniteDuration,
                                   resetWait: FiniteDuration) extends Actor with ActorLogging {
  private var failedCount = 0
  private var state: State = Close
  private lazy val threadPool: ExecutionContext = ExecutionContext.fromExecutor(new ForkJoinPool(1))
  private var timerToHalfOpen: Option[Cancellable] = None

  override def receive: Receive = sendToChild

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case _: ActorInitializationException => Stop
    case _: ActorKilledException         => Stop
    case _: DeathPactException           => Stop
    case t: Throwable =>
      onReceiveFailure(t)
      Stop
  }

  private def onReceiveFailure(t: Throwable): Unit = {
    log.debug(s"ReceiveFailure. state: $state, cause: $t")
    this.state match {
      case Close =>
        failedCount += 1
        if (failedCount >= maxFailCount) {
          failedCount = 0
          becomeOpen()
        }
      case Open | HalfOpen =>
        // re-fail on Open or failed messaging on HalfOpen...
        becomeOpen()
    }
  }

  private def internalBecome(_state: State, _receive: Receive) = {
    log.debug(s"state: $state => ${_state}")
    this.state = _state
    context.become(_receive)
  }

  private def becomeClose() = internalBecome(Close, sendToChild)

  private def becomeHalfOpen() = internalBecome(HalfOpen, sendToChild)

  private def becomeOpen() = {
    internalBecome(Open, responseException)
    // schedule to become `HalfOpen` state after defined `resetWait`.
    this.timerToHalfOpen.map { _.cancel() }
    this.timerToHalfOpen = Some(context.system.scheduler.scheduleOnce(resetWait, self, BecomeHalfOpen)(threadPool))
  }

  /**
   * send Message to child actor and receive message from the actor, proxy its result to the caller.
   * Only `Close` or `HalfOpen` state.
   */
  private def sendToChild: Receive = {
    case message: ExecuteMessage[T] =>
      // if fail, catch on `supervisorStrategy`
      log.debug(s"state: $state, message: $message")
      buildChildExecutorActor(message) ! Run
    case ChildSuccess(originalSender, result) =>
      log.debug(s"state: $state, result: $result")
      if (this.state == HalfOpen) becomeClose()
      // response from `ExecuteActor`, proxy to originalSender
      originalSender ! result
    case ChildFailure(originalSender, t) =>
      onReceiveFailure(t)
      originalSender ! Status.Failure(t)
  }

  private def buildChildExecutorActor(message: ExecuteMessage[T]): ActorRef =
    context actorOf ExecutorActor.props(sender, message, runTimeout)

  /**
   * not send Message to child actor, just return Exception to the caller
   * Only `Open` state.
   */
  private def responseException: Receive = {
    case BecomeHalfOpen =>
      if (this.state == Open) {
        becomeHalfOpen()
      }
    case Execute(run) =>
      log.debug(s"state: $state, received: $Execute")
      sender ! Status.Failure(new MessageOnOpenException(s"receive on `Open` state"))
    case ChildSuccess(originalSender, result) =>
      log.debug(s"state: $state, result: $result")
      if (this.state == HalfOpen) becomeClose()
      // response from `ExecuteActor`, proxy to originalSender
      originalSender ! result
    case ChildFailure(originalSender, t) =>
      onReceiveFailure(t)
      originalSender ! Status.Failure(t)
  }
}
