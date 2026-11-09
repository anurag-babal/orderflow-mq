package com.orderflow.playground.producer;

import static org.mockito.Mockito.verify;

import com.orderflow.playground.config.PlaygroundQueueProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class MessageProducerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    void sendPublishesToDefaultExchangeWithQueueNameAsRoutingKey() {
        MessageProducer producer = new MessageProducer(rabbitTemplate, new PlaygroundQueueProperties("playground.queue"));

        producer.send("hello");

        verify(rabbitTemplate).convertAndSend("", "playground.queue", "hello");
    }
}
