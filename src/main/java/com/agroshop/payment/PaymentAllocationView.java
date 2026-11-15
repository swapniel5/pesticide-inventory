package com.agroshop.payment;

import java.math.BigDecimal;

public record PaymentAllocationView(
        Long paymentId,
        Long saleId,
        String invoiceNumber,
        BigDecimal invoiceTotal,
        BigDecimal allocatedAmount) {
}
