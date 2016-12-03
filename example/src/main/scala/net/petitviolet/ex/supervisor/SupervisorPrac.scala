package net.petitviolet.ex.supervisor

import java.util.concurrent.ForkJoinPool

import akka.actor.{ ActorSystem, Props }
import akka.pattern.ask
import com.typesafe.config.ConfigFactory
import net.petitviolet.ex.common._
import net.petitviolet.supervisor._

import scala.concurrent.{ Future, ExecutionContext }
import scala.util.{ Random, Failure, Success }

object SupervisorPrac extends App {
  implicit val system = ActorSystem(s"SupervisorPrac")
  implicit val dispatcher: ExecutionContext = ExecutionContext.fromExecutor(new ForkJoinPool(1))

  val config = ConfigFactory.load().getConfig("supervisor")

  import scala.concurrent.duration._
  val supervisorActor = system.actorOf(Supervisor.props(
    maxFailCount = 2,
    runTimeout = 1000.milliseconds,
    resetWait = 3000.milliseconds
  ))

  val actorRef = system.actorOf(Props[UnstableActor])
  val future = Future.apply { val i = Random.nextInt(2000); Thread.sleep(i); i }

  // state is close
  supervisorActor ? Execute(future) onComplete {
    case Success(x) => println(s"success => $x")
    case Failure(t) => println(s"fail => $t")
  }
  supervisorActor ? Execute(actorRef ? Message("2")) onComplete { case x => println(s"result =>$x") }

  // failure once
  supervisorActor ? Execute(actorRef ? PanicMessage) onComplete { case x => println(s"result =>$x") }
  // sleep over `callTimeout`
  Thread.sleep(1500)
  // state is still close

  supervisorActor ? Execute(actorRef ? Message("3")) onComplete { case x => println(s"result =>$x") }
  // state is close

  // force open by over `maxFailure` failures
  supervisorActor ? Execute(actorRef ? PanicMessage) onComplete { case x => println(s"result =>$x") }
  supervisorActor ? Execute(actorRef ? HeavyMessage) onComplete { case x => println(s"result =>$x") }
  // wait Thread.sleep at receive `HeavyMesage`
  Thread.sleep(3000)
  // state gets open

  // message goes dead-queue
  supervisorActor ? Execute(actorRef ? Message("4")) onComplete { case x => println(s"result =>$x") }
  // state is still open
  Thread.sleep(3500)
  // state gets half-open after `resetTimeout`

  supervisorActor ? Execute(actorRef ? Message("5")) onComplete { case x => println(s"result =>$x") }
  // state gets close

  // still close
  Thread.sleep(1000)
  supervisorActor ? Execute(actorRef ? Message("6")) onComplete { case x => println(s"result =>$x") }
  supervisorActor ? Execute(actorRef ? Message("7")) onComplete { case x => println(s"result =>$x") }
  supervisorActor ? Execute(actorRef ? Message("8")) onComplete { case x => println(s"result =>$x") }

  // shutdown
  Thread.sleep(1000)
  system.terminate()
}
