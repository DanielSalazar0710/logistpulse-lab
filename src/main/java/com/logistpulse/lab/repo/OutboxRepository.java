package com.logistpulse.lab.repo;

import com.logistpulse.lab.domain.OutboxEvent;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Lote de eventos pendientes, en orden. FOR UPDATE SKIP LOCKED (timeout -2): si hubiera varias réplicas de
     * la API, cada una toma filas distintas y ningún evento se publica dos veces por concurrencia entre relays.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    List<OutboxEvent> findBySentAtIsNullOrderByIdAsc(Limit limit);

    long countBySentAtIsNull();
}
