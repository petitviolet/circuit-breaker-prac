package net.petitviolet.supervisor

import akka.actor._
import net.petitviolet.supervisor.ExecutorActor._

import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import scala.language.implicitConversions
import scala.util.{ Failure, Success, Try }

/**
 * Internal API
 */
private[supervisor] class ExecutorActor[T](originalSender: ActorRef,
                                           message: ExecuteMessage[T],
                                           timeout: FiniteDuration) extends Actor with ActorLogging {
  override def receive: Actor.Receive = {
    case Run =>
      log.debug(s"ExecutorActor: $message")
      Try { Await.result(message.run, timeout) } match {
        case Success(result) =>
          respondSuccessToParent(originalSender, result)
        case Failure(t) =>
          message match {
            case ExecuteWithFallback(_, fallback) =>
              respondSuccessToParent(originalSender, fallback)
            case _ =>
              respondFailureToParent(originalSender, t)
          }
      }
  }

  private def respondSuccessToParent(originalSender: ActorRef, result: T) = {
    sender() ! ChildSuccess(originalSender, result)
  }

  private def respondFailureToParent(originalSender: ActorRef, cause: Throwable) = {
    sender() ! ChildFailure(originalSender, cause)
  }
}

/**
 * Internal API
 */
private[supervisor] object ExecutorActor {
  object Run
  case class ChildSuccess[T](originalSender: ActorRef, result: T)
  case class ChildFailure(originalSender: ActorRef, cause: Throwable)

  def props[T](originalSender: ActorRef, execute: ExecuteMessage[T], timeout: FiniteDuration): Props =
    Props(classOf[ExecutorActor[T]], originalSender, execute, timeout)
}

