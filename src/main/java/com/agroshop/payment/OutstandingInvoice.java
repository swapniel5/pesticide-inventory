package com.agroshop.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OutstandingInvoice(
        Long saleId,
        String invoiceNumber,
        LocalDateTime saleDate,
        BigDecimal invoiceTotal,
        BigDecimal amountPaid,
        BigDecimal outstandingAmount) {
}
