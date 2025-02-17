package io.github.moonkev.vertx_playground.verticle

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vertx.core.Future
import io.vertx.core.VerticleBase
import io.vertx.rabbitmq.RabbitMQClient
import io.vertx.rabbitmq.RabbitMQOptions


class RmqServiceVerticle : VerticleBase() {

    val logger = KotlinLogging.logger { }

    override fun start(): Future<*> {
        val config = RabbitMQOptions()
            .setUser("guest")
            .setPassword("guest")
            .setHost("localhost")
            .setPort(5672)

        val client = RabbitMQClient.create(vertx, config)

        return client.start().flatMap { _ ->
            client.basicConsumer("fibonacci.queue")
        }.map { consumer ->
            consumer.handler { message ->
                logger.info { "Got message ${message.body()}" }
            }
        }
    }
}