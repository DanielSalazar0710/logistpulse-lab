package com.logistpulse.lab.domain;

import static com.logistpulse.lab.domain.OrderStatus.CANCELLED;
import static com.logistpulse.lab.domain.OrderStatus.CREATED;
import static com.logistpulse.lab.domain.OrderStatus.DELIVERED;
import static com.logistpulse.lab.domain.OrderStatus.DISPATCHED;
import static com.logistpulse.lab.domain.OrderStatus.IN_TRANSIT;
import static com.logistpulse.lab.domain.OrderStatus.RETURNED;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrderStatusTest {

    @Test
    void happyPath() {
        assertThat(CREATED.canGoTo(DISPATCHED)).isTrue();
        assertThat(DISPATCHED.canGoTo(IN_TRANSIT)).isTrue();
        assertThat(IN_TRANSIT.canGoTo(DELIVERED)).isTrue();
    }

    @Test
    void cannotSkipStates() {
        assertThat(CREATED.canGoTo(IN_TRANSIT)).isFalse();
        assertThat(CREATED.canGoTo(DELIVERED)).isFalse();
        assertThat(DISPATCHED.canGoTo(DELIVERED)).isFalse();
    }

    @Test
    void cannotCancelOnceInTransit() {
        assertThat(IN_TRANSIT.canGoTo(CANCELLED)).isFalse();
    }

    @Test
    void terminalStatesHaveNoExit() {
        assertThat(CANCELLED.next()).isEmpty();
        assertThat(RETURNED.next()).isEmpty();
    }

    // ---- Parte 4.1: estado RETURNED ----

    @Test
    void deliveredOrderCanBeReturned() {
        assertThat(DELIVERED.canGoTo(RETURNED)).isTrue();
        assertThat(DELIVERED.next()).containsExactly(RETURNED);
    }

    @Test
    void onlyDeliveredOrdersCanBeReturned() {
        for (OrderStatus s : OrderStatus.values()) {
            assertThat(s.canGoTo(RETURNED)).as("%s -> RETURNED", s).isEqualTo(s == DELIVERED);
        }
    }

    @Test
    void returnedIsTerminal() {
        for (OrderStatus s : OrderStatus.values()) {
            assertThat(RETURNED.canGoTo(s)).as("RETURNED -> %s", s).isFalse();
        }
    }

    @Test
    void returnedCannotBeRedeliveredNorCancelled() {
        assertThat(RETURNED.canGoTo(DELIVERED)).isFalse();
        assertThat(RETURNED.canGoTo(CANCELLED)).isFalse();
        assertThat(DELIVERED.canGoTo(CANCELLED)).isFalse();
    }

    @Test
    void noStateGoesBackToCreated() {
        for (OrderStatus s : OrderStatus.values()) {
            assertThat(s.canGoTo(CREATED)).as("%s -> CREATED", s).isFalse();
        }
    }
}
