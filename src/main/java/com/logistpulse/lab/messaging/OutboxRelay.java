package com.logistpulse.lab.messaging;

import com.logistpulse.lab.domain.OutboxEvent;
import com.logistpulse.lab.repo.OutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Parte 7.4 · Relay del Outbox: cada segundo toma los eventos pendientes, los publica en RabbitMQ y los marca
 * como enviados. Si el broker no responde, el lote se corta y se reintenta en la siguiente vuelta: los eventos
 * esperan en la base, no se pierden.
 *
 * Garantía: "al menos una vez". Si el proceso cae justo después de publicar y antes del commit que marca sent_at,
 * el evento se volverá a publicar; por eso el consumidor (FleetListener) es idempotente (Parte 7.5).
 *
 * Con lab.messaging.enabled=false (modo lite) no hay broker: se escribe en el log y se marca como enviado.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    static final int BATCH = 50;

    private final OutboxRepository outbox;
    private final ObjectProvider<RabbitTemplate> rabbit;
    private final boolean enabled;

    public OutboxRelay(OutboxRepository outbox, ObjectProvider<RabbitTemplate> rabbit,
                       @Value("${lab.messaging.enabled:false}") boolean enabled, MeterRegistry registry) {
        this.outbox = outbox;
        this.rabbit = rabbit;
        this.enabled = enabled;
        Gauge.builder("logistpulse.outbox.pending", outbox, OutboxRepository::countBySentAtIsNull)
                .description("Eventos en el outbox aún no publicados").register(registry);
    }

    @Scheduled(fixedDelayString = "${lab.outbox.poll-ms:1000}")
    @Transactional
    public void relay() {
        List<OutboxEvent> pending = outbox.findBySentAtIsNullOrderByIdAsc(Limit.of(BATCH));
        for (OutboxEvent e : pending) {
            if (!enabled) {
                log.info("[lite] evento {} id={} (RabbitMQ desactivado)", e.getEventType(), e.getAggregateId());
                e.markSent();
                continue;
            }
            try {
                rabbit.getObject().convertAndSend(RabbitTopology.DISPATCHED_QUEUE, e.getPayload(), m -> {
                    m.getMessageProperties().setMessageId("outbox-" + e.getId());
                    m.getMessageProperties().setType(e.getEventType());
                    return m;
                });
                e.markSent();
                log.info("Evento {} publicado id={} (outbox #{}, intento {})",
                        e.getEventType(), e.getAggregateId(), e.getId(), e.getAttempts());
            } catch (AmqpException ex) {
                e.markFailedAttempt();
                log.warn("RabbitMQ no disponible; {} evento(s) siguen en el outbox: {}",
                        outbox.countBySentAtIsNull(), ex.getMessage());
                break;   // el resto del lote espera a la siguiente vuelta, en orden
            }
        }
    }
}
