package com.logistpulse.lab.api.dto;

import com.logistpulse.lab.domain.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record TransitionInput(@NotNull(message = "indica el estado destino") OrderStatus to) {
}
