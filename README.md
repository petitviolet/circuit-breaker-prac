# Supervisor

A [circuit-breaker](http://martinfowler.com/bliki/CircuitBreaker.html) implementation with Akka.Actor.

CircuitBreaker provides stability and resilience.

# How to use

not released yet.

# Usage

I call circuit-breaker as `Supervisor`.
You can use it just like an `ActorRef`.

```scala
import net.petitviolet.supervisor._

import scala.concurrent.duration._
// create Supervisor instance
val supervisorActor: ActorRef = system.actorOf(Supervisor.props(
  maxFailCount = 2,
  runTimeout = 1000.milliseconds,
  resetWait = 3000.milliseconds
))

// other ActorRef
val actorRef = system.actorOf(Props[SomeYourActor])

// supervising asynchronous procedure which is enveloped by `Execute`
supervisorActor ? Execute(actorRef ? Message("1")) onComplete { 
  case Success(x) =>
    println(s"result =>$x") 
  case Failure(t) =>
    t.printStackTrace()
}
```
