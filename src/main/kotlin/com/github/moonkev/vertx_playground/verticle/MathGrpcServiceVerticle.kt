package com.github.moonkev.vertx_playground.verticle

import com.github.moonkev.math.v1.FibonacciResponse
import com.github.moonkev.math.v1.VertxMathGrpcServer
import com.github.moonkev.vertx_playground.codec.FibonacciResponseCodec
import io.github.oshai.kotlinlogging.KotlinLogging
import io.vertx.core.Future
import io.vertx.core.VerticleBase
import io.vertx.core.http.HttpServer
import io.vertx.grpc.common.GrpcStatus
import io.vertx.grpc.server.GrpcServer

class MathGrpcServiceVerticle : VerticleBase() {

    private val logger = KotlinLogging.logger {}

    override fun start(): Future<HttpServer> {
        val grpcServer = GrpcServer.server(vertx)
        val httpServer = vertx.createHttpServer()

        val eventBus = vertx.eventBus()
        eventBus.registerDefaultCodec(FibonacciResponse::class.java, FibonacciResponseCodec())

        grpcServer.callHandler(VertxMathGrpcServer.Fibonacci) { grpcRequest ->
            grpcRequest.messageHandler { message ->
                eventBus
                    .request<FibonacciResponse>("fibonacci.worker.raw", message.payload())
                    .onComplete { ar ->
                        if (ar.succeeded()) {
                            grpcRequest.response().end(ar.result().body())
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

        grpcServer.callHandler(VertxMathGrpcServer.FibonacciStream) { grpcRequest ->
            grpcRequest.handler { fibonacciRequest ->
                eventBus
                    .request<FibonacciResponse>("fibonacci.worker.raw", fibonacciRequest)
                    .onComplete { ar ->
                        if (ar.succeeded()) {
                            grpcRequest.response().write(ar.result().body())
                        } else {
                            grpcRequest
                                .response()
                                .status(GrpcStatus.INTERNAL)
                                .statusMessage("Failed to retrieve result from worker")
                                .end()
                        }
                    }
                grpcRequest.endHandler { grpcRequest.response().end() }
            }
        }

        return httpServer
            .requestHandler(grpcServer)
            .listen(config().getInteger("port")).onSuccess { server ->
                logger.info { "GRPC HTTP server on port ${server.actualPort()}" }
            }
    }
}
