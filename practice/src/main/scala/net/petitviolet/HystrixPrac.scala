package net.petitviolet

import java.util.concurrent.{Future => jFuture}

import com.netflix.hystrix.{HystrixCommand, HystrixCommandGroupKey}
import rx.Observable
import rx.functions.Action1

private object HystrixPrac extends App {
  val result1: Result = new UnstableCommand(500).execute()
  println(s"result1: $result1")

  val result2: jFuture[Result] = new UnstableCommand(1001).queue()
  try {
    println(s"result2: ${result2.get()}")
  } catch { case t: Throwable => println(s"result2 fail: $t")}

  val result3: Observable[Result] = new UnstableCommand(800).observe()
  result3.subscribe {
    new Action1[Result] {
      override def call(t: Result): Unit = println(s"result3: $t")
    }
  }

  Thread.sleep(3000)
}

private case class Result(value: String) extends AnyVal

private object UnstableCommand {
  val key = HystrixCommandGroupKey.Factory.asKey("my-command")
}
import UnstableCommand._
private class UnstableCommand(sleepMillis: Long) extends HystrixCommand[Result](key) {
  override protected def run(): Result = {
    Thread.sleep(sleepMillis)
    if (sleepMillis > 1000) { sys.error("Failed to run()")}
    Result(s"Command: $sleepMillis")
  }

  override protected def getFallback: Result = {
    Result(s"Ooops....: $sleepMillis")
  }
}
