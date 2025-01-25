package com.github.moonkev.vertx_playground

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vertx.config.ConfigRetriever
import io.vertx.config.ConfigRetrieverOptions
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
      port = Integer.getInteger("vertx.eventbus.port")))

  Vertx.builder().with(vertxOptions).withClusterManager(ZookeeperClusterManager()).buildClustered()
    .onComplete { res -> res.result() }
    .flatMap { vertx ->
      val hoconFileStore = configStoreOptionsOf(
        type = "file",
        format = "hocon",
        config = json { obj("path" to System.getProperty("config.file", "application.conf")) }
      )

      ConfigRetriever.create(vertx, ConfigRetrieverOptions().addStore(hoconFileStore)).config.flatMap { config ->
        val deploymentOptions = deploymentOptionsOf(config = config)
        vertx.deployVerticle(config.getString("verticle-name"), deploymentOptions)
      }
    }.onComplete { ar ->
      if (ar.succeeded()) {
        logger.info { "Deployed verticle: ${ar.result()}"}
      } else {
        logger.error (ar.cause()) { "Failed to deploy verticle" }
      }
    }

  System.`in`.read()
}
