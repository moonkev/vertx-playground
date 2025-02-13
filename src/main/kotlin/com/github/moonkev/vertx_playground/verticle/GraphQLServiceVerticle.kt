package com.github.moonkev.vertx_playground.verticle

import com.github.moonkev.math.v1.FibonacciRequest
import com.github.moonkev.math.v1.FibonacciResponse
import com.github.moonkev.vertx_playground.codec.FibonacciRequestCodec
import com.github.moonkev.vertx_playground.codec.FibonacciResponseCodec
import graphql.GraphQL
import graphql.execution.SubscriptionExecutionStrategy
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import io.github.oshai.kotlinlogging.KotlinLogging
import io.reactivex.rxjava3.core.Flowable
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.VerticleBase
import io.vertx.core.eventbus.Message
import io.vertx.core.http.HttpServer
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.graphql.GraphQLHandler
import io.vertx.ext.web.handler.graphql.ws.GraphQLWSHandler
import org.reactivestreams.Publisher
import javax.xml.crypto.Data
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

class GraphQLServiceVerticle : VerticleBase() {

    private val logger = KotlinLogging.logger { }

    companion object {
        private val SCHEMA = """
            type Query { 
                fibonacci(n: Int!): Int! 
                fibonacciLoadTest(n: Int!, count: Int!): String!
                
            }
            type Subscription {
                fibonacciSequence(n: Int!): Int!
            }
        """.trimIndent()
    }

    override fun start(): Future<HttpServer> {

        val eventBus = vertx.eventBus()
        eventBus.registerDefaultCodec(FibonacciRequest::class.java, FibonacciRequestCodec())
        eventBus.registerDefaultCodec(FibonacciResponse::class.java, FibonacciResponseCodec())
        val maxOutboundEvents: Int = config().getInteger("max-outbound-events", 1000)

        val schemaParser = SchemaParser()
        val typeRegistry = schemaParser.parse(SCHEMA)
        val runtimeWiring = RuntimeWiring.newRuntimeWiring()
            .type("Subscription") { builder ->
                builder.dataFetcher("fibonacciSequence") { env: DataFetchingEnvironment ->
                    val n: Int = env.getArgumentOrDefault("n", 0)
                    Flowable.fromIterable(0..n)
                }
            }
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
                    val count: Int = env.getArgumentOrDefault("count", 0)
                    val now = System.currentTimeMillis()

                    val requests = (1..count).toMutableList().map {
                        FibonacciRequest.newBuilder().setN(n).build()
                    }

                    val eventFutures = if (count <= maxOutboundEvents) {
                        requests.map { eventBus.request("fibonacci.worker.proto", it) }
                    } else {
                        var remaining = count
                        val promises = List(requests.size) { Promise.promise<Message<FibonacciResponse>>() }
                        fun schedule() {
                            val idx = count - remaining
                            val request = requests[idx]
                            eventBus
                                .request<FibonacciResponse>("fibonacci.worker.proto", request)
                                .onComplete { ar ->
                                    promises[idx].handle(ar)
                                    if (remaining != 0) {
                                        schedule()
                                    }
                                }
                            remaining -= 1
                        }
                        repeat(maxOutboundEvents) { schedule() }
                        promises.map { it.future() }
                    }

                    Future.all<Message<FibonacciResponse>>(eventFutures)
                        .map { vs ->
                            val totalExecTime = vs.list<Message<FibonacciResponse>>().fold(0L) { acc, res ->
                                acc + res.body().executionNanos
                            }
                            val averageTime = (totalExecTime.toDouble() / count).nanoseconds
                            val completionTime = (System.currentTimeMillis() - now).milliseconds
                            "Completion time = $completionTime, Average calculation time = $averageTime"
                        }
                        .toCompletionStage()
                }
            }
            .build()

        val graphqlSchema = SchemaGenerator().makeExecutableSchema(typeRegistry, runtimeWiring)
        val graphql = GraphQL.newGraphQL(graphqlSchema).subscriptionExecutionStrategy(SubscriptionExecutionStrategy()).build()
        val router = Router.router(vertx)

        router.post().handler(BodyHandler.create())
        router.route("/graphql").handler(GraphQLWSHandler.create(graphql))
        val httpServerOptions = HttpServerOptions().addWebSocketSubProtocol("graphql-transport-ws")

        return vertx
            .createHttpServer(httpServerOptions)
            .requestHandler(router)
            .listen(config().getInteger("port")).onSuccess { server ->
                logger.info { "GraphQL HTTP server started on port  ${server.actualPort()}" }
            }
    }
}