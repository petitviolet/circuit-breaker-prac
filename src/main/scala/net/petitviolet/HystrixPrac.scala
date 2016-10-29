package net.petitviolet

import com.netflix.hystrix.{HystrixCommandGroupKey, HystrixCommandKey, HystrixCommand}

object HystrixPrac extends App {
  val result1 = new UnstableCommand(500).execute()
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
