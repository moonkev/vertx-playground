package io.github.moonkev.vertx_playground

import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.vertx.config.ConfigRetriever
import io.vertx.config.ConfigRetrieverOptions
import io.vertx.core.ThreadingModel
import io.vertx.core.Vertx
import io.vertx.ext.cluster.infinispan.InfinispanClusterManager
import io.vertx.kotlin.config.configStoreOptionsOf
import io.vertx.kotlin.core.deploymentOptionsOf
import io.vertx.kotlin.core.eventbus.eventBusOptionsOf
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.core.vertxOptionsOf
import io.vertx.tracing.opentelemetry.OpenTelemetryOptions
import org.slf4j.bridge.SLF4JBridgeHandler


fun main() {

    val logger = KotlinLogging.logger { }

    SLF4JBridgeHandler.removeHandlersForRootLogger()
    SLF4JBridgeHandler.install();
    val spanExporter = OtlpJsonLoggingSpanExporter.create()

    val tracerProvider = SdkTracerProvider.builder()
        .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
        .build();

    val openTelemetry: OpenTelemetry = OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
        .buildAndRegisterGlobal()

    val vertxOptions = vertxOptionsOf(
        eventBusOptions = eventBusOptionsOf(
            host = System.getProperty("vertx.eventbus.host"),
            port = Integer.getInteger("vertx.eventbus.port")
        ),
        tracingOptions = OpenTelemetryOptions(openTelemetry)
    )

    Vertx.builder().with(vertxOptions).withClusterManager(InfinispanClusterManager()).buildClustered()
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
