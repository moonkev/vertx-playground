package com.github.moonkev.vertx_playground

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vertx.config.ConfigRetriever
import io.vertx.config.ConfigRetrieverOptions
import io.vertx.core.DeploymentOptions
import io.vertx.core.ThreadingModel
import io.vertx.core.Vertx
import io.vertx.kotlin.config.configStoreOptionsOf
import io.vertx.kotlin.core.deploymentOptionsOf
import io.vertx.kotlin.core.eventbus.eventBusOptionsOf
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.core.vertxOptionsOf
import io.vertx.spi.cluster.zookeeper.ZookeeperClusterManager

fun main() {

    val logger = KotlinLogging.logger { }

    val vertxOptions = vertxOptionsOf(
        eventBusOptions = eventBusOptionsOf(
            host = System.getProperty("vertx.eventbus.host"),
            port = Integer.getInteger("vertx.eventbus.port")
        )
    )

    Vertx.builder().with(vertxOptions).withClusterManager(ZookeeperClusterManager()).buildClustered()
        .onComplete { res -> res.result() }
        .flatMap { vertx ->
            val configType = System.getProperty("config.type", "file")
            val configStore = when (configType) {
                "http" -> configStoreOptionsOf(
                    type = "http",
                    format = System.getProperty("config.format", "hocon"),
                    config = json {
                        obj(
                            "host" to System.getProperty("config.host", "localhost"),
                            "port" to Integer.getInteger("config.port", 8080),
                            "path" to System.getProperty("config.path", "application.conf")
                        )
                    }
                )

                else -> configStoreOptionsOf(
                    type = "file",
                    format = System.getProperty("config.format", "hocon"),
                    config = json { obj("path" to System.getProperty("config.path", "application.conf")) }
                )
            }
            ConfigRetriever.create(vertx, ConfigRetrieverOptions().addStore(configStore)).config.flatMap { config ->
                DeploymentOptions()
                val deploymentOptions =
                    deploymentOptionsOf(config = config, threadingModel = ThreadingModel.VIRTUAL_THREAD)
                vertx.deployVerticle(config.getString("verticle-name"), deploymentOptions)
            }
                .onSuccess { verticleId ->
                    logger.info { "Deployed verticle: $verticleId" }
                }
                .onFailure { error ->
                    logger.error(error) { "Failed to deploy verticle" }
                    vertx.close()
                }
        }
}
