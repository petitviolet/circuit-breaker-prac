package net.petitviolet.cb.akka

import java.util.concurrent.TimeUnit

import akka.actor.Actor.Receive
import akka.actor.SupervisorStrategy.{Stop, Restart}
import akka.actor._
import akka.pattern.ask
import com.typesafe.config.{ConfigFactory, Config}
import net.petitviolet.cb.akka.ExecutorActor.{ChildResult, Run}
import net.petitviolet.cb.akka.Supervisor.MessageOnOpenException

import scala.concurrent.{Await, Future}
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
  private def resetWait(config: Config): FiniteDuration = Duration(config.getLong("rest-wait"), TimeUnit.MILLISECONDS)
}

final class Supervisor[T] private(maxFailCount: Int, runTimeout: FiniteDuration, resetWait: FiniteDuration) extends Actor {
  private var failedCount = 0
  private var state: State = Open

  override def receive: Receive = sendToChild

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case _: ActorInitializationException => Stop
    case _: ActorKilledException         => Stop
    case _: DeathPactException           => Stop
    case t: Throwable =>
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
      Restart
  }

  private def becomeClose() = {
    this.state = Close
    context.become(sendToChild)
  }

  private def becomeHalfOpen() = {
    this.state = HalfOpen
    context.become(sendToChild)
  }

  private def becomeOpen() = {
    this.state = Open
    context.become(responseException)
    // schedule to become `HalfOpen` state after defined `resetWait`.
    context.system.scheduler.scheduleOnce(resetWait, new Runnable {
      override def run(): Unit = becomeHalfOpen()
    })(context.dispatcher)
  }

  /**
   * send Message to child actor and receive message from the actor, proxy its result to the caller.
   * Only `Close` or `HalfOpen` state.
   */
  private def sendToChild: Receive = {
    case message: ExecuteMessage[T] =>
      // if fail, catch on `supervisorStrategy`
      buildChildExecutorActor(message) ! Run
    case ChildResult(originalSender, result) =>
      if (this.state == HalfOpen) becomeClose()
      // response from `ExecuteActor`, proxy to originalSender
      originalSender ! result
  }

  private def buildChildExecutorActor(message: ExecuteMessage[T]): ActorRef =
    context actorOf ExecutorActor.props(sender, message, runTimeout)


  /**
   * not send Message to child actor, just return Exception to the caller
   * Only `Open` state.
   */
  private def responseException: Receive = {
    case Execute => new MessageOnOpenException(s"receive on `Open` state").printStackTrace()
  }
}

/**
 * Internal API
 */
private class ExecutorActor[T](originalSender: ActorRef,
                               message: ExecuteMessage[T],
                               timeout: FiniteDuration) extends Actor {
  override def receive: Actor.Receive = {
    case Run =>
      val resultTry: Try[T] = Try { Await.result(message.run, timeout) }
      resultTry match {
        case Success(result) => originalSender ! result
        case Failure(t) =>
          message match {
            case ExecuteWithFallback(_, fallback) => originalSender ! fallback
            case _ => throw t
          }
      }
  }
}

/**
 * Internal API
 */
private object ExecutorActor {
  object Run
  case class ChildResult[T](originalSender: ActorRef, result: T)

  def props[T](originalSender: ActorRef, execute: ExecuteMessage[T], timeout: FiniteDuration): Props =
    Props(classOf[ExecutorActor[T]], originalSender, execute, timeout)
}

