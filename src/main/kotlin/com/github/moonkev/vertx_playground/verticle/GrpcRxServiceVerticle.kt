package com.github.moonkev.vertx_playground.verticle

import com.github.moonkev.math.v1.FibonacciRequest
import com.github.moonkev.math.v1.FibonacciResponse
import com.github.moonkev.math.v1.VertxMathGrpcServer
import com.github.moonkev.vertx_playground.codec.FibonacciRequestCodec
import com.github.moonkev.vertx_playground.codec.FibonacciResponseCodec
import io.github.oshai.kotlinlogging.KotlinLogging
import io.vertx.grpc.common.GrpcStatus
import io.vertx.rxjava3.core.AbstractVerticle
import io.vertx.rxjava3.grpc.common.ServiceMethod
import io.vertx.rxjava3.grpc.server.GrpcServer

class GrpcRxServiceVerticle : AbstractVerticle() {

    private val logger = KotlinLogging.logger {}

    companion object GrpcRxServiceVerticle {
        private val FIBONACCI_METHOD =
            ServiceMethod.newInstance<FibonacciRequest, FibonacciResponse>(VertxMathGrpcServer.Fibonacci)

        private val FIBONACCI_STREAM_METHOD =
            ServiceMethod.newInstance<FibonacciRequest, FibonacciResponse>(VertxMathGrpcServer.FibonacciStream)
    }

    override fun start() {
        val grpcServer = GrpcServer.server(vertx)
        val httpServer = vertx.createHttpServer()

        val eventBus = vertx.eventBus()
        eventBus.delegate.registerDefaultCodec(FibonacciRequest::class.java, FibonacciRequestCodec())
        eventBus.delegate.registerDefaultCodec(FibonacciResponse::class.java, FibonacciResponseCodec())

        grpcServer.callHandler(FIBONACCI_METHOD) { grpcRequest ->
            grpcRequest
                .toFlowable()
                .flatMap { message ->
                    eventBus.rxRequest<FibonacciResponse>("fibonacci.worker.proto", message).toFlowable()
                }
                .map { it.body() }
                .subscribe (
                    { grpcRequest.response().end(it) },
                    {
                        grpcRequest
                            .response()
                            .status(GrpcStatus.INTERNAL)
                            .statusMessage("Failed to retrieve result from worker")
                            .end()
                    }
                )
        }

        grpcServer.callHandler(FIBONACCI_STREAM_METHOD) { grpcRequest ->
            grpcRequest
                .toFlowable()
                .flatMap { eventBus.rxRequest<FibonacciResponse>("fibonacci.worker.proto", it).toFlowable() }
                .map { it.body() }
                .subscribe(
                    { grpcRequest.response().write(it) },
                    { logger.error { "Error in fibonacci worker request: ${it.message}" } },
                    {
                        logger.info { "FibonacciStream closed" }
                        grpcRequest.response().end()
                    }
                )
        }

        httpServer
            .requestHandler(grpcServer)
            .listen(config().getInteger("port")).subscribe { server ->
                logger.info { "GRPC HTTP server on port ${server.actualPort()}" }
            }
    }
}
