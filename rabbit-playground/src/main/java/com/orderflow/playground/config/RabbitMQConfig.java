package com.orderflow.playground.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the RabbitMQ topology (what must exist in the broker) for the playground.
 * Spring AMQP's RabbitAdmin creates every Queue/Exchange/Binding bean on the broker
 * when the first connection is opened. Declaring an existing, identical queue is a no-op.
 */
@Configuration
public class RabbitMQConfig {

    @Bean
    public Queue playgroundQueue(PlaygroundQueueProperties properties) {
        return QueueBuilder
                .durable(properties.name()) // survives a broker restart (queue definition only, see Phase 7)
                .build();
    }
}
