package com.example.service.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Kafka support is OFF by default.
 *
 * To enable:
 *   1. set app.kafka.enabled=true (application.yml or env APP_KAFKA_ENABLED=true)
 *   2. configure spring.kafka.* (bootstrap-servers, consumer/producer)
 *   3. add @KafkaListener on your listener beans
 *
 * Spring Boot auto-configures producer/consumer factories from spring.kafka.*
 * so no manual bean wiring is required in the template.
 */
@Configuration
@EnableKafka
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true")
public class KafkaConfig {
}
