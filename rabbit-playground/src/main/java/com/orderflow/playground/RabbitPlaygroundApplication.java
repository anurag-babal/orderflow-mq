package com.orderflow.playground;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RabbitPlaygroundApplication {

    public static void main(String[] args) {
        SpringApplication.run(RabbitPlaygroundApplication.class, args);
    }
}
