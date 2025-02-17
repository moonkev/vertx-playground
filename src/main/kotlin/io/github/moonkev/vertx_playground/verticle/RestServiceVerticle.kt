package io.github.moonkev.vertx_playground.verticle

import io.github.moonkev.math.v1.FibonacciRequest
import io.github.moonkev.math.v1.VertxMathGrpcClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.vertx.core.Future
import io.vertx.core.VerticleBase
import io.vertx.core.http.HttpServer
import io.vertx.core.json.JsonObject
import io.vertx.core.net.SocketAddress
import io.vertx.ext.web.Router
import io.vertx.grpc.client.GrpcClient
import io.vertx.kotlin.core.json.get
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj

class RestServiceVerticle : VerticleBase() {

    private val logger = KotlinLogging.logger { }

    override fun start(): Future<HttpServer> {
        val router = Router.router(vertx)
        val grpcConfig: JsonObject = config()["math-grpc-service"]
        val mathGrpcServer = SocketAddress.inetSocketAddress(grpcConfig["port"], grpcConfig["host"])
        val grpcClient = GrpcClient.client(vertx)

        router.route().handler { context ->

            val address = context.request().connection().remoteAddress().toString()
            val queryParams = context.queryParams()
            val num = queryParams.get("num")?.toInt() ?: 0

            grpcClient.request(mathGrpcServer, VertxMathGrpcClient.Fibonacci)
                .compose { request ->
                    request.end(FibonacciRequest.newBuilder().setN(num).build())
                    request.response().compose { response -> response.last() }
                }
                .onComplete { ar ->
                    if (ar.succeeded()) {
                        context.json(
                            json {
                                obj(
                                    "status" to "success",
                                    "num" to num,
                                    "address" to address,
                                    "message" to "fibonacci($num) = ${ar.result().result}"
                                )
                            }
                        )
                    } else {
                        context.json(
                            json {
                                obj("status" to "failed ${ar.cause().message}")
                            }
                        )
                    }
                }
        }

        return vertx
            .createHttpServer()
            .requestHandler(router)
            .listen(config().getInteger("port")).onSuccess { server ->
                logger.info { "HTTP server started on port  ${server.actualPort()}" }
            }
    }
}
