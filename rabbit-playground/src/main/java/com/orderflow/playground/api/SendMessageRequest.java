package com.orderflow.playground.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /messages}.
 */
public record SendMessageRequest(
        @NotBlank(message = "message must not be blank")
        @Size(max = 1000, message = "message must be at most 1000 characters")
        String message) {
}
