package net.petitviolet.ex

import java.util.concurrent.{ Future => jFuture }

import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext
import com.netflix.hystrix.{ HystrixCommand, HystrixCommandGroupKey, HystrixCommandProperties }
import rx.Observable
import rx.functions.Action1

object HystrixPracCB extends App {
  val cacheContext: HystrixRequestContext =
    HystrixRequestContext.initializeContext()

  def nowTime = System.currentTimeMillis() - start
  def logging(i: Int, result: Any) = println(s"now($i) => $nowTime: $result")

  val start = System.currentTimeMillis()
  println(s"start")
  var i = 0
  while (i < 30) {
    Thread.sleep(20)
    val command = new MyCommand(i)
    try {
      val observable = command.observe()

      observable.subscribe(
        (r: Result) => logging(i, r),
        (t: Throwable) => logging(i, t),
        () => ()
      )
    } catch {
      case t: Throwable =>
        logging(i, t)
    }
    i += 1
  }
  println(s"end: $nowTime")
  Thread.sleep(1000)
}

private object HystrixPracSimple extends App {
  val cacheContext: HystrixRequestContext =
    HystrixRequestContext.initializeContext()
  val start = System.currentTimeMillis()

  // synchronous execute
  val result1: Result = new MyCommand(500).execute()
  println(s"result1: $result1")
  println(s"Time 1: ${System.currentTimeMillis() - start}")

  // async execute, Java Future
  val result2: jFuture[Result] = new MyCommand(1001).queue()
  try {
    println(s"result2: ${result2.get()}")
  } catch { case t: Throwable => println(s"result2 fail: $t") }
  println(s"Time 2: ${System.currentTimeMillis() - start}")

  // async execute, with Observable
  val result3: Observable[Result] = new MyCommand(800).observe()
  result3.subscribe { (t: Result) =>
    { // SAM!!! Scala2.12!!!
      println(s"result3: $t")
      println(s"Time 3: ${System.currentTimeMillis() - start}")
    }
  }

  // async execute, with Observable using cache
  val result4: Observable[Result] = new MyCommand(500).observe()
  result4.subscribe { (t: Result) =>
    {
      println(s"result4: $t")
      println(s"Time 4: ${System.currentTimeMillis() - start}")
    }
  }

  // wait for subscribe observable
  Thread.sleep(3000)
}

private case class Result(value: String) extends AnyVal

private object MyCommand {
  val key = HystrixCommandGroupKey.Factory.asKey("my-command")

  private val circuitBreakerSetter =
    HystrixCommandProperties.Setter()
      .withCircuitBreakerEnabled(true)
      .withCircuitBreakerRequestVolumeThreshold(2)
      .withCircuitBreakerErrorThresholdPercentage(10)
      .withCircuitBreakerSleepWindowInMilliseconds(100)

  val setter: HystrixCommand.Setter =
    HystrixCommand.Setter
      .withGroupKey(key)
      .andCommandPropertiesDefaults(circuitBreakerSetter)
}

/**
 * use Circuit-Breaker with implementing [[HystrixCommand]]
 *
 * @param num
 */
private class MyCommand(num: Long) extends HystrixCommand[Result](MyCommand.setter) {
  //  MyCommand.key) {

  //  println(s"config: ${this.getProperties.circuitBreakerErrorThresholdPercentage().get()}")

  override def execute(): Result = {
    print(s"open? : $isCircuitBreakerOpen\t")
    super.execute()
  }

  /**
   * will be invoked on { execute, queue, observe }
   *
   * @return
   */
  override protected def run(): Result = {
    if (num % 2 == 0) { sys.error("Failed to run()") }
    Result(s"Command: $num")
  }

  /**
   * fallback value for when `run` failed
   *
   * @return
   */
  //  override protected def getFallback: Result = {
  //    Result(s"Ooops....: $num")
  //  }

  /**
   * cache key
   * [[HystrixCommand]] is enabled to cache result of `run`
   *
   * @return
   */
  //  override protected def getCacheKey: String = num.toString
}
