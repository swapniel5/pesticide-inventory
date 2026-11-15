package com.agroshop.customer;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record CustomerFinancialSummary(
        BigDecimal totalPurchases,
        BigDecimal totalPaymentsReceived,
        BigDecimal outstandingBalance) {

    public static CustomerFinancialSummary from(BigDecimal totalPurchases, BigDecimal totalPaymentsReceived) {
        BigDecimal purchases = currencyAmount(totalPurchases);
        BigDecimal payments = currencyAmount(totalPaymentsReceived);
        return new CustomerFinancialSummary(purchases, payments, purchases.subtract(payments));
    }

    private static BigDecimal currencyAmount(BigDecimal amount) {
        return (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
    }
}
