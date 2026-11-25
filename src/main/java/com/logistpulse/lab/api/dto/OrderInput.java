package com.logistpulse.lab.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OrderInput(
        @Pattern(regexp = "^LP-[A-Z0-9]{3,10}$", message = "código con formato LP-XXXX") String code,
        @NotBlank(message = "el cliente es obligatorio") @Size(max = 80, message = "máximo 80 caracteres") String customer,
        @Min(value = 0, message = "debe ser ≥ 0") int destX,
        @Min(value = 0, message = "debe ser ≥ 0") int destY) {
}
