package com.orderflow.playground.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Type-safe binding for the {@code playground.queue.*} keys in application.yml.
 */
@ConfigurationProperties(prefix = "playground.queue")
public record PlaygroundQueueProperties(String name) {

    public PlaygroundQueueProperties {
        if (!StringUtils.hasText(name)) {
            throw new IllegalStateException("playground.queue.name must be configured");
        }
    }
}
