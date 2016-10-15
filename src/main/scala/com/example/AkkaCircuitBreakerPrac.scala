package com.example

import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.{CircuitBreaker, ask}
import akka.util.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object AkkaCircuitBreakerPrac extends App {
  private case class Message(value: String)
  private case object UnsafeMessage

  private class UnstableActor extends Actor {
    override def receive: Receive = {
      case Message(value) =>
        println(value); sender ! s"receive: $value"
      case UnsafeMessage =>
        // sleep over callTimeout
        Thread.sleep(1100); println("Oops...")
    }
  }
  implicit val system =ActorSystem(s"AkkaCircuitBreakerPrac")
  implicit val dispatcher: ExecutionContext = ExecutionContext.Implicits.global

  val circuitBreaker = CircuitBreaker(
    system.scheduler,
    maxFailures = 2,
    callTimeout =  1.seconds,
    resetTimeout =  3.seconds)
    .onOpen(println(s"OPEN"))
    .onClose(println(s"CLOSE"))
    .onHalfOpen(println(s"HALF-OPEN"))

  val actorRef = system.actorOf(Props[UnstableActor])
  implicit val timeout = Timeout(5.seconds)

  circuitBreaker.withCircuitBreaker(actorRef ? Message("1"))
  circuitBreaker.withCircuitBreaker(actorRef ? Message("2"))

  // force open
  circuitBreaker.withCircuitBreaker(actorRef ? UnsafeMessage)
  // state is close
  // sleep over callTimeout
  Thread.sleep(1500)
  // state is close

  circuitBreaker.withCircuitBreaker(actorRef ? Message("3"))
  // state is close

  // force open with over maxFailure failures
  circuitBreaker.withCircuitBreaker(actorRef ? UnsafeMessage)
  circuitBreaker.withCircuitBreaker(actorRef ? UnsafeMessage)
  Thread.sleep(3000)
  // state gets open

  // message goes dead-queue
  circuitBreaker.withCircuitBreaker(actorRef ? Message("4"))
  // state is open
  Thread.sleep(3500)
  // state gets half-open

  circuitBreaker.withCircuitBreaker(actorRef ? Message("5"))
  // state gets close

  // stil close
  Thread.sleep(1000)
  circuitBreaker.withCircuitBreaker(actorRef ? Message("6"))
  circuitBreaker.withCircuitBreaker(actorRef ? Message("7"))
  circuitBreaker.withCircuitBreaker(actorRef ? Message("8"))
  Thread.sleep(1000)
  system.terminate()
}

