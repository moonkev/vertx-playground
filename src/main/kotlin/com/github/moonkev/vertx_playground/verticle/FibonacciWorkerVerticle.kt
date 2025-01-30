package com.github.moonkev.vertx_playground.verticle

import com.github.moonkev.math.v1.FibonacciRequest
import com.github.moonkev.math.v1.FibonacciResponse
import com.github.moonkev.vertx_playground.codec.FibonacciRequestCodec
import com.github.moonkev.vertx_playground.codec.FibonacciResponseCodec
import io.github.oshai.kotlinlogging.KotlinLogging
import io.vertx.core.Future
import io.vertx.core.VerticleBase
import io.vertx.core.buffer.Buffer
import java.time.ZonedDateTime

class FibonacciWorkerVerticle : VerticleBase() {

    private val logger = KotlinLogging.logger {}

    private fun calculate(n: Int): Int {
        if (n <= 1) {
            return n
        }
        return calculate(n - 1) + calculate(n - 2)
    }

    private fun response(request: FibonacciRequest): FibonacciResponse {
        val start = System.nanoTime()
        ZonedDateTime.now()
        val calculated = calculate(request.n)
        logger.debug { "fibonacci for ${request.n} calculated: $calculated" }
        val duration = System.nanoTime() - start
        return FibonacciResponse
            .newBuilder()
            .setN(request.n)
            .setResult(calculated)
            .setExecutionNanos(duration)
            .build()
    }

    override fun start(): Future<*> {
        val eventBus = vertx.eventBus()

        eventBus.registerDefaultCodec(FibonacciRequest::class.java, FibonacciRequestCodec())
        eventBus.registerDefaultCodec(FibonacciResponse::class.java, FibonacciResponseCodec())

        eventBus.consumer<Buffer>("fibonacci.worker.raw") {
            it.reply(response(FibonacciRequest.parseFrom(it.body().bytes)))
        }

        eventBus.consumer<FibonacciRequest>("fibonacci.worker.proto") {
            it.reply(response(it.body()))
        }

        return super.start()
    }
}
