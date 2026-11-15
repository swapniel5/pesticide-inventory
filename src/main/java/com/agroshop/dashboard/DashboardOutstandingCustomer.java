package com.agroshop.dashboard;

import java.math.BigDecimal;

public record DashboardOutstandingCustomer(
        Long customerId,
        String customerName,
        String phone,
        String village,
        BigDecimal outstandingBalance,
        boolean active) {
}
