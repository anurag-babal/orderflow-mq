package com.orderflow.playground.producer;

import com.orderflow.playground.config.PlaygroundQueueProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Producer: publishes text messages to the playground queue.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageProducer {

    /** Empty name = the broker's built-in "default exchange" (explained in Step 7). */
    private static final String DEFAULT_EXCHANGE = "";

    private final RabbitTemplate rabbitTemplate;
    private final PlaygroundQueueProperties queueProperties;

    public void send(String message) {
        // On the default exchange, routing key == queue name.
        rabbitTemplate.convertAndSend(DEFAULT_EXCHANGE, queueProperties.name(), message);
        log.info("Sent message to queue '{}': {}", queueProperties.name(), message);
    }
}
