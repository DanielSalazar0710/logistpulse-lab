package com.logistpulse.lab.messaging;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "lab.messaging.enabled", havingValue = "true")
public class RabbitTopology {

    public static final String DISPATCHED_QUEUE = "orders.dispatched";
    public static final String DISPATCHED_DLQ = "orders.dispatched.dlq";

    /**
     * Parte 7.5: un mensaje que falla en el intento y en los 3 reintentos del listener ya no vuelve a la cola en bucle:
     * se rechaza sin reencolar y RabbitMQ lo manda a la dead-letter queue para revisarlo a mano.
     */
    @Bean
    Queue dispatchedQueue() {
        return QueueBuilder.durable(DISPATCHED_QUEUE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(DISPATCHED_DLQ)
                .build();
    }

    @Bean
    Queue dispatchedDeadLetterQueue() {
        return QueueBuilder.durable(DISPATCHED_DLQ).build();
    }
}
