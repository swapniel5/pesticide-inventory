package com.agroshop.inventory;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StockAdjustmentRequest(
        @NotNull(message = "Movement type is required") MovementType movementType,
        @NotNull(message = "Quantity is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "Quantity must be greater than zero")
        BigDecimal quantity,
        @Size(max = 500, message = "Remarks must be 500 characters or fewer") String remarks) {
}
