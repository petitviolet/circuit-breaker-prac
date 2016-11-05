package net.petitviolet.prac

import java.util.concurrent.{ Future => jFuture }

import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext
import com.netflix.hystrix.{ HystrixCommand, HystrixCommandGroupKey }
import rx.Observable
import rx.functions.Action1

private object HystrixPrac extends App {
  val cacheContext: HystrixRequestContext =
    HystrixRequestContext.initializeContext()
  val start = System.currentTimeMillis()

  // synchronous execute
  val result1: Result = new UnstableCommand(500).execute()
  println(s"result1: $result1")
  println(s"Time 1: ${System.currentTimeMillis() - start}")

  // async execute, Java Future
  val result2: jFuture[Result] = new UnstableCommand(1001).queue()
  try {
    println(s"result2: ${result2.get()}")
  } catch { case t: Throwable => println(s"result2 fail: $t") }
  println(s"Time 2: ${System.currentTimeMillis() - start}")

  // async execute, with Observable
  val result3: Observable[Result] = new UnstableCommand(800).observe()
  result3.subscribe {
    new Action1[Result] {
      override def call(t: Result): Unit = {
        println(s"result3: $t")
        println(s"Time 3: ${System.currentTimeMillis() - start}")
      }
    }
  }

  // async execute, with Observable using cache
  val result4: Observable[Result] = new UnstableCommand(500).observe()
  result4.subscribe {
    new Action1[Result] {
      override def call(t: Result): Unit = {
        println(s"result4: $t")
        println(s"Time 4: ${System.currentTimeMillis() - start}")
      }
    }
  }

  // wait for subscribe observable
  Thread.sleep(3000)
}

private case class Result(value: String) extends AnyVal

private object UnstableCommand {
  val key = HystrixCommandGroupKey.Factory.asKey("my-command")
}
import net.petitviolet.prac.UnstableCommand._

/**
 * use Circuit-Breaker with implementing [[HystrixCommand]]
 *
 * @param sleepMillis
 */
private class UnstableCommand(sleepMillis: Long) extends HystrixCommand[Result](key) {
  /**
   * will be invoked on { execute, queue, observe }
   *
   * @return
   */
  override protected def run(): Result = {
    Thread.sleep(sleepMillis)
    if (sleepMillis > 1000) { sys.error("Failed to run()") }
    Result(s"Command: $sleepMillis")
  }

  /**
   * fallback value for when `run` failed
   *
   * @return
   */
  override protected def getFallback: Result = {
    Result(s"Ooops....: $sleepMillis")
  }

  /**
   * cache key
   * [[HystrixCommand]] is enabled to cache result of `run`
   *
   * @return
   */
  override protected def getCacheKey: String = sleepMillis.toString
}
