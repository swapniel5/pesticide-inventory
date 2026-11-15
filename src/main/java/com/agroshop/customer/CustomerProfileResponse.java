package com.agroshop.customer;

import java.util.List;

public record CustomerProfileResponse(
        CustomerResponse customer,
        List<CustomerPurchaseHistoryItem> purchaseHistory,
        CustomerFinancialSummary financialSummary) {
}
