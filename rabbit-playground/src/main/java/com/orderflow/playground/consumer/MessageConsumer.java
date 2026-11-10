package com.orderflow.playground.consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumer: receives text messages from the playground queue.
 */
@Slf4j
@Component
public class MessageConsumer {

    /**
     * Invoked by the listener container once for every message delivered by the broker.
     * If this method returns normally, the container sends an ACK. If it throws, the
     * message is rejected (and, by default, requeued; see the notes in the Step 5 docs).
     */
    @RabbitListener(queues = "${playground.queue.name}")
    public void receive(String message) {
        log.info("Received message from queue: {}", message);
    }
}
