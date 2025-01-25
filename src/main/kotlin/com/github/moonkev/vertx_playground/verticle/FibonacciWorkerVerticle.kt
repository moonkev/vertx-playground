package com.github.moonkev.vertx_playground.verticle

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vertx.core.AbstractVerticle

class FibonacciWorkerVerticle : AbstractVerticle() {

  private val logger = KotlinLogging.logger {}

  private fun calculate(n: Long): Long {
    if (n <= 1) {
      return n
    }
    return calculate(n - 1) + calculate(n - 2)
  }

  override fun start() {
    val eventBus = vertx.eventBus()

    eventBus.consumer<Int>("fibonacci.worker") {
      val n = it.body().toLong()
      val calculated = calculate(n)
      logger.info { "fibonacci for $n calculated: $calculated" }
      it.reply(calculated)
    }
  }
}
