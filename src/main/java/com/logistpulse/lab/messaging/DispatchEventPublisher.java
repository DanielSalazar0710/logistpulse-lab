package com.logistpulse.lab.messaging;

import com.logistpulse.lab.domain.OutboxEvent;
import com.logistpulse.lab.repo.OutboxRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Parte 7.4 · Ya no publica "después del commit": registra el evento en la tabla outbox_event dentro de la
 * transacción del despacho (Propagation.MANDATORY: llamarlo fuera de una transacción es un error de programación).
 *
 * Antes (afterCommit) quedaba una ventana: commit hecho y envío fallido o proceso caído = evento perdido, pedido
 * DISPATCHED para siempre. Ahora el evento es tan durable como el cambio de estado y {@link OutboxRelay} lo
 * publica cuando RabbitMQ esté disponible (entrega "al menos una vez").
 */
@Component
public class DispatchEventPublisher {

    public static final String ORDER_DISPATCHED = "order.dispatched";

    private final OutboxRepository outbox;

    public DispatchEventPublisher(OutboxRepository outbox) {
        this.outbox = outbox;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(Long orderId) {
        outbox.save(new OutboxEvent(ORDER_DISPATCHED, orderId, String.valueOf(orderId)));
    }
}
