package com.agroshop.payment;

import java.math.BigDecimal;

public record SaleAllocationTotal(Long saleId, BigDecimal allocatedAmount) {
}
