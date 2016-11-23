# Supervisor

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/net.petitviolet/supervisor_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/net.petitviolet/supervisor_2.12)
[![CircleCI](https://circleci.com/gh/petitviolet/supervisor/tree/master.svg?style=svg)](https://circleci.com/gh/petitviolet/supervisor/tree/master)

A [circuit-breaker](http://martinfowler.com/bliki/CircuitBreaker.html) implementation with Akka.Actor.

CircuitBreaker provides stability and resilience.

# How to use

write dependency in your build.sbt.

```
libraryDependencies += "net.petitviolet" %% "supervisor" % "<latest version>"
// libraryDependencies += "net.petitviolet" % "supervisor_2.12" % "<latest version>"
```

# Usage

I call circuit-breaker as `Supervisor`.
You can use it just like an `ActorRef`.

## simple example

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

## Creation Supervisor

`Supervisor` needs 3 values.

- `maxFailCount`
    - if failure count gets more than equal this limit, `Supervisor` state gets `Open`
- `runTimeout`
    - wait time for asynchronous process by `Supervisor`
- `resetWait`
    - when `Supervisor` state is `Open` and past this duration, its state gets `Half-Open`

### specifying above values

```scala
import net.petitviolet.supervisor._

import scala.concurrent.duration._
// create Supervisor instance
val supervisorActor: ActorRef = system.actorOf(Supervisor.props(
  maxFailCount = 2,
  runTimeout = 1000.milliseconds,
  resetWait = 3000.milliseconds
))
```

### specifying at configuration file

Using [Typesafe Config](https://github.com/typesafehub/config) library.

In some conf file, for example application.conf, write like below.

```
supervisor {
  max-fail-count = 2
  run-timeout = 1000
  reset-wait = 3000
}
```

The key `supervisor` is modifiable, but others are not.

```scala
val config = ConfigFactory.load().getConfig("supervisor")

val supervisorActor: ActorRef = system.actorOf(Supervisor.props(config))
```

## use `Supervisor` as a CircuitBreaker

`ActorRef` instance of `Supervisor` supervises asynchronous procedures.
When you wrap your `scala.concurrent.Future` instance in `Execute` and `tell` or `ask` to it, it works as a CircuitBreaker.

```scala
// other ActorRef for example to create Future.
val actorRef = system.actorOf(Props[SomeYourActor])

// supervising asynchronous procedure which is enveloped by `Execute`.
// Of cource you can use `tell` instead of `ask`, when you do not need result of Future.
supervisorActor ? Execute(actorRef ? Message("1")) onComplete { 
  case Success(x) =>
    println(s"result =>$x") 
  case Failure(t) =>
    t.printStackTrace()
}
```

# `Supervisor` state

There are the same 3 states as CircuitBreaker.
Simple description of each states is below.

- Close
    - normal state.
    - `Supervisor` try to given process done normally with waiting `runTimeout` duration.
- Open
    - Aborting `Future`.
    - When the state is Close and the count of `Future` gets `Failure` or exceeded `runTimeout`, it's state gets Open.
- Half-Open
    - on the way of it's state to Close from Open.
        - It's state is Open and past `resetWait` duration, it gets Half-Open.
    - It make only 1 try, when `Success`, gets Close, otherwise when `Failure`, gets Open.
