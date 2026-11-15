package com.agroshop.inventory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StockMovementResponse(
        Long id,
        Long productId,
        String productName,
        String unit,
        MovementType movementType,
        BigDecimal quantity,
        String remarks,
        LocalDateTime createdAt) {

    public static StockMovementResponse from(StockMovement movement) {
        return new StockMovementResponse(
                movement.getId(),
                movement.getProduct().getId(),
                movement.getProduct().getName(),
                movement.getProduct().getUnit(),
                movement.getMovementType(),
                movement.getQuantity(),
                movement.getRemarks(),
                movement.getCreatedAt());
    }
}
