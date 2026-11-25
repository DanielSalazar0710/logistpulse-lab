package com.logistpulse.lab.domain;

import java.util.EnumSet;
import java.util.Set;

/** Máquina de estados del pedido (el "Formal Decision Gate" del laboratorio). */
public enum OrderStatus {
    CREATED, DISPATCHED, IN_TRANSIT, DELIVERED, CANCELLED,
    /** Parte 4.1: el cliente devuelve un pedido ya entregado (logística inversa). Estado terminal. */
    RETURNED;

    public Set<OrderStatus> next() {
        return switch (this) {
            case CREATED -> EnumSet.of(DISPATCHED, CANCELLED);
            case DISPATCHED -> EnumSet.of(IN_TRANSIT, CANCELLED);
            case IN_TRANSIT -> EnumSet.of(DELIVERED);
            case DELIVERED -> EnumSet.of(RETURNED);
            case CANCELLED, RETURNED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canGoTo(OrderStatus target) {
        return next().contains(target);
    }
}
