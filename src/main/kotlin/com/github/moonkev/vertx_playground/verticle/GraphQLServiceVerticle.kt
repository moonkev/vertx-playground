package com.github.moonkev.vertx_playground.verticle

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
                    val n: Int? = env.getArgument("n")
                    eventBus.request<Int>("fibonacci.worker", n).map { f -> f.body() }
                        .toCompletionStage()
                }
                builder.dataFetcher("fibonacciLoadTest") { env: DataFetchingEnvironment ->
                    val n: Int? = env.getArgument("n")
                    val count: Int = env.getArgumentOrDefault("count", 0)
                    val now = System.currentTimeMillis()
                    val eventFutures = List(count) { eventBus.request< Message<Int>>("fibonacci.worker", n) }
                    Future.all<Int>(eventFutures)
                        .map{
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