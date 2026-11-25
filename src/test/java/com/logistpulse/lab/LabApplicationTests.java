package com.logistpulse.lab;

import static org.assertj.core.api.Assertions.assertThat;

import com.logistpulse.lab.api.dto.OrderDto;
import com.logistpulse.lab.api.dto.OrderInput;
import com.logistpulse.lab.domain.OrderStatus;
import com.logistpulse.lab.repo.OutboxRepository;
import com.logistpulse.lab.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Prueba de integración con el contexto completo de Spring en modo lite (H2): valida que las migraciones
 * Flyway de H2 coinciden con las entidades (ddl-auto=validate) y que el despacho deja su evento en el outbox.
 * El transportista no está levantado, así que también ejercita la degradación a ETA local.
 * (Era uno de los puntos de "deuda técnica reconocida": no había pruebas con contexto de Spring.)
 */
@SpringBootTest(properties = {"lab.carrier.base-url=http://127.0.0.1:9", "lab.outbox.poll-ms=600000"})
class LabApplicationTests {

    @Autowired
    OrderService service;

    @Autowired
    OutboxRepository outbox;

    @Test
    void createAndDispatchWritesTheEventToTheOutbox() {
        long before = outbox.countBySentAtIsNull();
        OrderDto created = service.create(new OrderInput("LP-IT0001", "Integración", 10, 5));
        OrderDto dispatched = service.dispatch(created.id());

        assertThat(dispatched.status()).isEqualTo(OrderStatus.DISPATCHED);
        assertThat(dispatched.robot()).isNotNull();
        assertThat(dispatched.etaMinutes()).isPositive();
        assertThat(outbox.countBySentAtIsNull()).isEqualTo(before + 1);
    }
}
