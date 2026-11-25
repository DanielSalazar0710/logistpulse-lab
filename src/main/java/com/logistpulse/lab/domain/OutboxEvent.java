package com.logistpulse.lab.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Parte 7.4 · Patrón Outbox: el evento se guarda en la MISMA transacción que el cambio de estado del pedido.
 * O se confirman los dos o ninguno; un proceso aparte (OutboxRelay) lo publica después en RabbitMQ.
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false)
    private Long aggregateId;

    @Column(nullable = false, length = 500)
    private String payload;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /** null mientras no se haya publicado. */
    private Instant sentAt;

    private int attempts;

    protected OutboxEvent() {
    }

    public OutboxEvent(String eventType, Long aggregateId, String payload) {
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payload = payload;
    }

    public Long getId() { return id; }
    public String getEventType() { return eventType; }
    public Long getAggregateId() { return aggregateId; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSentAt() { return sentAt; }
    public int getAttempts() { return attempts; }

    public void markSent() {
        this.sentAt = Instant.now();
        this.attempts++;
    }

    public void markFailedAttempt() {
        this.attempts++;
    }
}
