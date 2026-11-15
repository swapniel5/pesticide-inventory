package com.agroshop.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PaymentView(
        Long id,
        Long customerId,
        String customerName,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        LocalDateTime paymentDate,
        String transactionReference,
        String notes,
        List<PaymentInvoiceAllocation> allocations) {

    public PaymentView {
        allocations = List.copyOf(allocations);
    }

    public String reference() {
        return transactionReference == null || transactionReference.isBlank()
                ? "PAY-" + id
                : transactionReference;
    }
}
