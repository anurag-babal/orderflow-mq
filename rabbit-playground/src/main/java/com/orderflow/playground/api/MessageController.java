package com.orderflow.playground.api;

import com.orderflow.playground.producer.MessageProducer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageProducer messageProducer;

    /**
     * Publishes the message and returns 202 Accepted: the message was handed to the broker,
     * but nobody has processed it yet. That is the asynchronous contract.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> send(@Valid @RequestBody SendMessageRequest request) {
        messageProducer.send(request.message());
        return Map.of("status", "accepted");
    }
}
