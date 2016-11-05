package net.petitviolet.akka.supervisor

import akka.actor._
import net.petitviolet.akka.supervisor.ExecutorActor._

import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}

/**
 * Internal API
 */
private[supervisor] class ExecutorActor[T](originalSender: ActorRef,
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
private[supervisor] object ExecutorActor {
  object Run
  case class ChildSuccess[T](originalSender: ActorRef, result: T)
  case class ChildFailure(originalSender: ActorRef, cause: Throwable)

  def props[T](originalSender: ActorRef, execute: ExecuteMessage[T], timeout: FiniteDuration): Props =
    Props(classOf[ExecutorActor[T]], originalSender, execute, timeout)
}


