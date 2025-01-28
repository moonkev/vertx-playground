package com.github.moonkev.vertx_playground.verticle

import com.github.moonkev.math.v1.FibonacciRequest
import com.github.moonkev.math.v1.FibonacciResponse
import com.github.moonkev.vertx_playground.codec.FibonacciRequestCodec
import com.github.moonkev.vertx_playground.codec.FibonacciResponseCodec
import graphql.GraphQL
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import io.github.oshai.kotlinlogging.KotlinLogging
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.eventbus.Message
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.graphql.GraphQLHandler

class GraphQLServiceVerticle : AbstractVerticle() {

    private val logger = KotlinLogging.logger { }

    override fun start() {

        val eventBus = vertx.eventBus()
        eventBus.registerDefaultCodec(FibonacciRequest::class.java, FibonacciRequestCodec())
        eventBus.registerDefaultCodec(FibonacciResponse::class.java, FibonacciResponseCodec())

        val schema = """
            type Query { 
                fibonacci(n: Int!): Int! 
                fibonacciLoadTest(n: Int!, count: Int!): String!
            }
        """.trimIndent()
        val schemaParser = SchemaParser()
        val typeRegistry = schemaParser.parse(schema)
        val runtimeWiring = RuntimeWiring.newRuntimeWiring()
            .type("Query") { builder ->
                builder.dataFetcher("fibonacci") { env: DataFetchingEnvironment ->
                    val n: Int = env.getArgumentOrDefault("n", 0)
                    val request = FibonacciRequest.newBuilder().setN(n).build()
                    eventBus
                        .request<FibonacciResponse>("fibonacci.worker.proto", request)
                        .map { f -> f.body().result }
                        .toCompletionStage()
                }
                builder.dataFetcher("fibonacciLoadTest") { env: DataFetchingEnvironment ->
                    val n: Int = env.getArgumentOrDefault("n", 0)
                    val request = FibonacciRequest.newBuilder().setN(n).build()
                    val count: Int = env.getArgumentOrDefault("count", 0)
                    val now = System.currentTimeMillis()
                    val requests = (1..count).toMutableList()
                    fun schedule(): Future<FibonacciResponse> {
                        return if (requests.isNotEmpty()) {
                            requests.removeFirst()
                            eventBus
                                .request<Message<Int>>("fibonacci.worker.proto", request)
                                .flatMap{ schedule() }
                        } else {
                            Future.succeededFuture(FibonacciResponse.getDefaultInstance())
                        }
                    }
                    val eventFutures = List(100) { schedule() }
                    Future.all<Int>(eventFutures)
                        .map {
                            val completionMillis = System.currentTimeMillis() - now
                            "Completion of $count Fibonacci Requests took $completionMillis ms"
                        }
                        .toCompletionStage()
                }
            }
            .build()
        val graphqlSchema = SchemaGenerator().makeExecutableSchema(typeRegistry, runtimeWiring)
        val graphql = GraphQL.newGraphQL(graphqlSchema).build()
        val router = Router.router(vertx)

        router.post().handler(BodyHandler.create())
        router.route("/graphql").handler(GraphQLHandler.create(graphql))

        vertx
            .createHttpServer()
            .requestHandler(router)
            .listen(config().getInteger("port")).onSuccess { server ->
                logger.info { "GraphQL HTTP server started on port  ${server.actualPort()}" }
            }
    }
}