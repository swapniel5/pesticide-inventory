package com.agroshop.payment;

import java.math.BigDecimal;

public record PaymentInvoiceAllocation(
        Long saleId,
        String invoiceNumber,
        BigDecimal invoiceTotal,
        BigDecimal allocatedAmount) {
}
