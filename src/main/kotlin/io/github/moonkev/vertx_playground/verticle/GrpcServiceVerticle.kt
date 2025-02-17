package io.github.moonkev.vertx_playground.verticle

import io.github.moonkev.math.v1.FibonacciRequest
import io.github.moonkev.math.v1.FibonacciResponse
import io.github.moonkev.math.v1.VertxMathGrpcServer
import io.github.moonkev.vertx_playground.codec.FibonacciRequestCodec
import io.github.moonkev.vertx_playground.codec.FibonacciResponseCodec
import io.github.oshai.kotlinlogging.KotlinLogging
import io.vertx.core.Future
import io.vertx.core.VerticleBase
import io.vertx.core.http.HttpServer
import io.vertx.grpc.common.GrpcStatus
import io.vertx.grpc.server.GrpcServer

class GrpcServiceVerticle : VerticleBase() {

    private val logger = KotlinLogging.logger {}

    override fun start(): Future<HttpServer> {
        val grpcServer = GrpcServer.server(vertx)
        val httpServer = vertx.createHttpServer()

        val eventBus = vertx.eventBus()
        eventBus.registerDefaultCodec(FibonacciRequest::class.java, FibonacciRequestCodec())
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
            grpcRequest.messageHandler { message ->
                eventBus
                    .request<FibonacciResponse>("fibonacci.worker.raw", message.payload())
                    .onComplete { ar ->
                        if (ar.succeeded()) {
                            grpcRequest.response().write(ar.result().body())
                        } else {
                            logger.error { "Error in fibonacci worker request: ${ar.cause().message}"}
                        }
                    }
                grpcRequest.endHandler {
                    logger.info { "FibonacciStream closed"}
                    grpcRequest.response().end()
                }
            }
        }

        return httpServer
            .requestHandler(grpcServer)
            .listen(config().getInteger("port")).onSuccess { server ->
                logger.info { "GRPC HTTP server on port ${server.actualPort()}" }
            }
    }
}
