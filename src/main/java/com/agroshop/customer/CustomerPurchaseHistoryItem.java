package com.agroshop.customer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

import com.agroshop.payment.PaymentStatus;
import com.agroshop.sales.SaleStatus;

public record CustomerPurchaseHistoryItem(
        Long saleId,
        String invoiceNumber,
        LocalDateTime saleDate,
        BigDecimal invoiceTotal,
        BigDecimal amountPaid,
        SaleStatus status) {

    public CustomerPurchaseHistoryItem {
        if (amountPaid == null || status != SaleStatus.COMPLETED) {
            amountPaid = new BigDecimal("0.00");
        } else {
            amountPaid = amountPaid.setScale(2, RoundingMode.HALF_UP);
        }
    }

    public BigDecimal outstandingAmount() {
        return status == SaleStatus.COMPLETED
                ? invoiceTotal.subtract(amountPaid)
                : new BigDecimal("0.00");
    }

    public String getPaymentStatusLabel() {
        return PaymentStatus.forInvoice(status, invoiceTotal, amountPaid).label();
    }
}
