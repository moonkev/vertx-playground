package com.github.moonkev.vertx_playground.verticle

import com.github.moonkev.math.v1.FibonacciRequest
import com.github.moonkev.math.v1.VertxMathGrpcClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.vertx.core.Future
import io.vertx.core.VerticleBase
import io.vertx.core.json.JsonObject
import io.vertx.core.net.SocketAddress
import io.vertx.grpc.client.GrpcClient
import io.vertx.kotlin.core.json.get
import kotlin.random.Random

class FibonacciBotVerticle : VerticleBase() {

    val logger = KotlinLogging.logger { }
    override fun start(): Future<*> {
        val client = GrpcClient.client(vertx)
        val grpcConfig: JsonObject = config()["math-grpc-service"]
        val maxN = config().getInteger("max-n", 10)
        val interval = config().getLong("spam-interval", 50)
        val spamLogThreshold = config().getInteger("spam-log-threshold", 100)
        val mathGrpcServer = SocketAddress.inetSocketAddress(grpcConfig["port"], grpcConfig["host"])

        return client
            .request(mathGrpcServer, VertxMathGrpcClient.FibonacciStream)
            .compose { request ->
                var spamCount = 0
                val timerId = vertx.setPeriodic(interval) { _ ->
                    val n = Random.nextInt(maxN)
                    request.write(FibonacciRequest.newBuilder().setN(n).build())
                    spamCount++
                    if (spamCount % spamLogThreshold == 0) {
                        logger.info { "FibonacciBot has spammed $spamCount requests" }
                    }
                }
                request.response().map { Pair(timerId, it) }
            }.onSuccess { (timerId, grpcClientResponse) ->
                grpcClientResponse.handler { fibonacciResponse ->
                    logger.debug { "Fibonacci(${fibonacciResponse.n}) = ${fibonacciResponse.result}" }
                }
                grpcClientResponse.exceptionHandler { error ->
                    logger.error(error) { "Error processing FibonacciRequest" }
                }
                grpcClientResponse.endHandler { _ ->
                    logger.debug { "FibonacciStream closed" }
                    vertx.cancelTimer(timerId)
                    grpcClientResponse.end()
                }
            }
    }
}