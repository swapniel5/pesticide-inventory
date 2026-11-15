package com.agroshop.payment;

import java.math.BigDecimal;

import com.agroshop.sales.SaleStatus;

public enum PaymentStatus {
    PAID("PAID"),
    PARTIALLY_PAID("PARTIALLY PAID"),
    UNPAID("UNPAID"),
    CANCELLED("CANCELLED");

    private final String label;

    PaymentStatus(String label) {
        this.label = label;
    }

    public static PaymentStatus forInvoice(SaleStatus saleStatus, BigDecimal invoiceTotal, BigDecimal amountPaid) {
        if (saleStatus == SaleStatus.CANCELLED) {
            return CANCELLED;
        }
        if (amountPaid == null || amountPaid.signum() <= 0) {
            return UNPAID;
        }
        return amountPaid.compareTo(invoiceTotal) >= 0 ? PAID : PARTIALLY_PAID;
    }

    public String label() {
        return label;
    }
}
