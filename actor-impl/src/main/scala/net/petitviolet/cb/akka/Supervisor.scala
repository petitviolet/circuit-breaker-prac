package net.petitviolet.cb.akka

import java.util.concurrent.{ForkJoinPool, TimeUnit}

import akka.actor.Actor.Receive
import akka.actor.SupervisorStrategy.{Stop, Restart}
import akka.actor._
import akka.pattern.ask
import com.typesafe.config.{ConfigFactory, Config}
import net.petitviolet.cb.akka.ExecutorActor.{ChildFailure, ChildSuccess, Run}
import net.petitviolet.cb.akka.Supervisor.{BecomeHalfOpen, MessageOnOpenException}

import scala.concurrent.{ExecutionContext, Await, Future}
import scala.concurrent.duration.{FiniteDuration, Duration}
import scala.language.implicitConversions
import scala.util.{Try, Failure, Success}

private sealed trait State
private case object Close extends State
private case object HalfOpen extends State
private case object Open extends State

sealed trait Message extends Any

sealed trait ExecuteMessage[T] extends Message {
  val run: Future[T]
}

case class Execute[T] private (run: Future[T]) extends ExecuteMessage[T]

case class ExecuteWithFallback[T](run: Future[T], fallback: T) extends ExecuteMessage[T]

object Supervisor {
  case class MessageOnOpenException private[akka] (msg: String) extends RuntimeException(msg)

  def props[T](config: Config): Props =
    Props(classOf[Supervisor[T]], maxFailCount(config), runTimeout(config), resetWait(config))

  def props[T](maxFailCount: Int, runTimeout: FiniteDuration, resetWait: FiniteDuration): Props =
    Props(classOf[Supervisor[T]], maxFailCount, runTimeout, resetWait)

  private def maxFailCount(config: Config): Int = config.getInt("max-fail-count")
  private def runTimeout(config: Config): FiniteDuration = Duration(config.getLong("run-timeout"), TimeUnit.MILLISECONDS)
  private def resetWait(config: Config): FiniteDuration = Duration(config.getLong("reset-wait"), TimeUnit.MILLISECONDS)

  private[akka] case object BecomeHalfOpen
}

final class Supervisor[T] private(maxFailCount: Int,
                                  runTimeout: FiniteDuration,
                                  resetWait: FiniteDuration) extends Actor with ActorLogging {
  private var failedCount = 0
  private var state: State = Close

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
    if (this.state == HalfOpen) {
      // failed messaging on HalfOpen...
      becomeOpen()
    } else {
      failedCount += 1
      if (failedCount >= maxFailCount) {
        failedCount = 0
        becomeOpen()
      }
    }
  }

  private def becomeClose() = {
    log.info(s"state: $state => Close")
    this.state = Close
    context.become(sendToChild)
  }

  private def becomeHalfOpen() = {
    log.info(s"state: $state => HalfOpen")
    this.state = HalfOpen
    context.become(sendToChild)
  }

  private def becomeOpen() = {
    log.info(s"state: $state => Open")
    this.state = Open
    context.become(responseException)
    // schedule to become `HalfOpen` state after defined `resetWait`.
    context.system.scheduler.scheduleOnce(resetWait, self, BecomeHalfOpen)(ExecutionContext.fromExecutor(new ForkJoinPool(1)))
  }

  /**
   * send Message to child actor and receive message from the actor, proxy its result to the caller.
   * Only `Close` or `HalfOpen` state.
   */
  private def sendToChild: Receive = {
    case message: ExecuteMessage[T] =>
      // if fail, catch on `supervisorStrategy`
      log.info(s"state: $state, message: $message")
      buildChildExecutorActor(message) ! Run
    case ChildSuccess(originalSender, result) =>
      log.info(s"state: $state, result: $result")
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

/**
 * Internal API
 */
private class ExecutorActor[T](originalSender: ActorRef,
                               message: ExecuteMessage[T],
                               timeout: FiniteDuration) extends Actor with ActorLogging {
  override def receive: Actor.Receive = {
    case Run =>
      log.debug(s"ExecutorActor: $message")
      val resultTry: Try[T] = Try { Await.result(message.run, timeout) }
      resultTry match {
        case Success(result) =>
          respondToParent(originalSender, result)
        case Failure(t) =>
          message match {
            case ExecuteWithFallback(_, fallback) =>
              respondToParent(originalSender, fallback)
            case _ =>
              sender ! ChildFailure(originalSender, t)
          }
      }
  }

  private def respondToParent(originalSender: ActorRef, result: T) = {
    sender() ! ChildSuccess(originalSender, result)
  }
}

/**
 * Internal API
 */
private object ExecutorActor {
  object Run
  case class ChildSuccess[T](originalSender: ActorRef, result: T)
  case class ChildFailure(originalSender: ActorRef, cause: Throwable)

  def props[T](originalSender: ActorRef, execute: ExecuteMessage[T], timeout: FiniteDuration): Props =
    Props(classOf[ExecutorActor[T]], originalSender, execute, timeout)
}

