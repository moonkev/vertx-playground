package com.github.moonkev.vertx_playground.verticle

import com.github.moonkev.math.v1.FibonacciResponse
import com.github.moonkev.math.v1.VertxMathGrpcServer
import io.github.oshai.kotlinlogging.KotlinLogging
import io.vertx.core.AbstractVerticle
import io.vertx.grpc.common.GrpcStatus
import io.vertx.grpc.server.GrpcServer

class MathGrpcServiceVerticle : AbstractVerticle() {

  private val logger = KotlinLogging.logger {}

  override fun start() {
    val grpcServer = GrpcServer.server(vertx)
    val httpServer = vertx.createHttpServer()
    val eventBus = vertx.eventBus()

    grpcServer.callHandler(VertxMathGrpcServer.Fibonacci) { grpcRequest ->
      grpcRequest.handler { fibonacciRequest ->
        eventBus
          .request<Long>("fibonacci.worker", fibonacciRequest.n)
          .onComplete {ar ->
            val result = ar.result().body()
            if (ar.succeeded()) {
              val response = FibonacciResponse.newBuilder().setResult(result).build()
              grpcRequest.response().end(response)
            } else {
                grpcRequest
                  .response()
                  .status(GrpcStatus.INTERNAL)
                  .statusMessage("Failed to retrieve result from worker")
                  .end()
            }
          }
      }
    }

    httpServer
      .requestHandler(grpcServer)
      .listen(config().getInteger("port")).onSuccess { server ->
        logger.info { "GRPC HTTP server on port ${server.actualPort()}" }
      }
  }
}
