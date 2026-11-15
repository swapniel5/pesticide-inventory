package com.agroshop.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RecentPaymentSummary(
        Long paymentId,
        String transactionReference,
        Long customerId,
        String customerName,
        LocalDateTime paymentDate,
        BigDecimal amount,
        PaymentMethod paymentMethod) {

    public String reference() {
        return transactionReference == null || transactionReference.isBlank()
                ? "PAY-" + paymentId
                : transactionReference;
    }
}
