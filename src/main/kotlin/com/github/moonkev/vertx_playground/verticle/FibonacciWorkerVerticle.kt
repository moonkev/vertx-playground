package com.github.moonkev.vertx_playground.verticle

import com.github.moonkev.math.v1.FibonacciRequest
import com.github.moonkev.math.v1.FibonacciResponse
import com.github.moonkev.vertx_playground.codec.FibonacciRequestCodec
import com.github.moonkev.vertx_playground.codec.FibonacciResponseCodec
import io.github.oshai.kotlinlogging.KotlinLogging
import io.vertx.core.AbstractVerticle
import io.vertx.core.buffer.Buffer

class FibonacciWorkerVerticle : AbstractVerticle() {

  private val logger = KotlinLogging.logger {}

  private fun calculate(n: Int): Int {
    if (n <= 1) {
      return n
    }
    return calculate(n - 1) + calculate(n - 2)
  }

  private fun response(request: FibonacciRequest): FibonacciResponse {
    val calculated = calculate(request.n)
    logger.debug { "fibonacci for ${request.n} calculated: $calculated" }
    return FibonacciResponse.newBuilder().setResult(calculated).build()
  }

  override fun start() {
    val eventBus = vertx.eventBus()

    eventBus.registerDefaultCodec(FibonacciRequest::class.java, FibonacciRequestCodec())
    eventBus.registerDefaultCodec(FibonacciResponse::class.java, FibonacciResponseCodec())

    eventBus.consumer<Buffer>("fibonacci.worker.raw") {
      it.reply(response(FibonacciRequest.parseFrom(it.body().bytes)))
    }

    eventBus.consumer<FibonacciRequest>("fibonacci.worker.proto") {
      it.reply(response(it.body()))
    }
  }
}
