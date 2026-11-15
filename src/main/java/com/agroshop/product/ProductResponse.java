package com.agroshop.product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductResponse(
        Long id,
        String name,
        String brand,
        String unit,
        BigDecimal quantity,
        BigDecimal sellingPrice,
        BigDecimal lowStockThreshold,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getBrand(),
                product.getUnit(),
                product.getQuantity(),
                product.getSellingPrice(),
                product.getLowStockThreshold(),
                product.isActive(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }

    public boolean isLowStock() {
        return active && quantity.compareTo(lowStockThreshold) <= 0;
    }
}
