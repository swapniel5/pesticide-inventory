package com.agroshop.sales;

import java.math.BigDecimal;

public record SaleItemResponse(
        Long productId,
        String productName,
        String unit,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal) {

    public static SaleItemResponse from(SaleItem item) {
        return new SaleItemResponse(
                item.getProduct().getId(),
                item.getProductNameSnapshot(),
                item.getProduct().getUnit(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getLineTotal());
    }
}
