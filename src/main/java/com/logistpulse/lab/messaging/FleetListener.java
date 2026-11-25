package com.logistpulse.lab.messaging;

import com.logistpulse.lab.service.OrderService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Simula la flota: al recibir el evento, el robot sale y el pedido pasa a IN_TRANSIT.
 *
 * Parte 7.5 · Consumidor idempotente. Con el Outbox la entrega es "al menos una vez", así que el mismo evento
 * puede llegar dos veces. Antes, el segundo intento lanzaba "Transición ilegal IN_TRANSIT → IN_TRANSIT", el
 * mensaje volvía a la cola y se reentregaba miles de veces por segundo (mensaje venenoso). Ahora la transición
 * es condicional (solo si el pedido sigue DISPATCHED): procesar el evento N veces deja el mismo resultado que 1.
 *
 * Parte 7.2: lab.fleet.auto-startup=false arranca la API sin consumidor para ver cómo se acumulan los mensajes.
 */
@Component
@ConditionalOnProperty(name = "lab.messaging.enabled", havingValue = "true")
public class FleetListener {

    private static final Logger log = LoggerFactory.getLogger(FleetListener.class);

    private final OrderService orders;
    private final Counter duplicates;

    public FleetListener(OrderService orders, MeterRegistry registry) {
        this.orders = orders;
        this.duplicates = Counter.builder("logistpulse.fleet.duplicates")
                .description("Eventos order.dispatched repetidos o tardíos ignorados").register(registry);
    }

    @RabbitListener(queues = RabbitTopology.DISPATCHED_QUEUE, autoStartup = "${lab.fleet.auto-startup:true}")
    public void onDispatched(String orderId, @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
        log.info("Flota recibió order.dispatched id={} ({})", orderId, messageId);
        boolean applied = orders.startTransitIfDispatched(Long.valueOf(orderId.trim()));
        if (!applied) {
            duplicates.increment();
            log.info("Evento duplicado o tardío ignorado: el pedido {} ya no está DISPATCHED", orderId);
        }
    }
}
