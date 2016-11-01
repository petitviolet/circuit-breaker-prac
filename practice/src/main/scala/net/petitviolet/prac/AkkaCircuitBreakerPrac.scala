package net.petitviolet.prac

import akka.actor.{ActorSystem, Props}
import akka.pattern.{CircuitBreaker, ask}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object AkkaCircuitBreakerPrac extends App {
  implicit val system = ActorSystem(s"AkkaCircuitBreakerPrac")
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

  // state is close
  circuitBreaker.withCircuitBreaker(actorRef ? Message("1"))
  circuitBreaker.withCircuitBreaker(actorRef ? Message("2"))

  // failure once
  circuitBreaker.withCircuitBreaker(actorRef ? PanicMessage)
  // sleep over `callTimeout`
  Thread.sleep(1500)
  // state is still close

  circuitBreaker.withCircuitBreaker(actorRef ? Message("3"))
  // state is close

  // force open by over `maxFailure` failures
  circuitBreaker.withCircuitBreaker(actorRef ? PanicMessage)
  circuitBreaker.withCircuitBreaker(actorRef ? HeavyMessage)
  // wait Thread.sleep at receive `HeavyMesage`
  Thread.sleep(3000)
  // state gets open

  // message goes dead-queue
  circuitBreaker.withCircuitBreaker(actorRef ? Message("4"))
  // state is still open
  Thread.sleep(3500)
  // state gets half-open after `resetTimeout`

  circuitBreaker.withCircuitBreaker(actorRef ? Message("5"))
  // state gets close

  // still close
  Thread.sleep(1000)
  circuitBreaker.withCircuitBreaker(actorRef ? Message("6"))
  circuitBreaker.withCircuitBreaker(actorRef ? Message("7"))
  circuitBreaker.withCircuitBreaker(actorRef ? Message("8"))

  // shutdown
  Thread.sleep(1000)
  system.terminate()
}

