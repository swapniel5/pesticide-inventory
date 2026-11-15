package com.agroshop.sales;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

import com.agroshop.payment.PaymentStatus;

public record RecentSaleSummary(
        Long saleId,
        String invoiceNumber,
        LocalDateTime createdAt,
        String customerName,
        BigDecimal totalAmount,
        BigDecimal amountPaid,
        SaleStatus status) {

    public RecentSaleSummary {
        amountPaid = (amountPaid == null ? BigDecimal.ZERO : amountPaid).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal outstandingBalance() {
        return totalAmount.subtract(amountPaid).setScale(2, RoundingMode.HALF_UP);
    }

    public String getPaymentStatusLabel() {
        return PaymentStatus.forInvoice(status, totalAmount, amountPaid).label();
    }
}
