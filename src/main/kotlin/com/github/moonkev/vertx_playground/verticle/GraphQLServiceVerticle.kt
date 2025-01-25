package com.github.moonkev.vertx_playground.verticle

import graphql.GraphQL
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import io.github.oshai.kotlinlogging.KotlinLogging
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.graphql.GraphQLHandler
import java.util.concurrent.CompletableFuture

class GraphQLServiceVerticle : AbstractVerticle() {

    private val logger = KotlinLogging.logger { }

    override fun start() {

        val eventBus = vertx.eventBus()
        val schema = "type Query { fibonacci(n: Int!): Int! }"
        val schemaParser = SchemaParser()
        val typeRegistry = schemaParser.parse(schema)
        val runtimeWiring = RuntimeWiring.newRuntimeWiring()
            .type("Query") { builder ->
                builder.dataFetcher("fibonacci") { env: DataFetchingEnvironment ->
                    //TODO: FIX THIS CASTING CRAP
                    val n = env.getArgumentOrDefault<Int>("n", 0)
                    eventBus.request<Long>("fibonacci.worker", n.toLong()).map { f -> f.body().toInt() }
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