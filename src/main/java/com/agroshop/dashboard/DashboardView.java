package com.agroshop.dashboard;

import java.math.BigDecimal;
import java.util.List;

import com.agroshop.payment.RecentPaymentSummary;
import com.agroshop.product.ProductResponse;
import com.agroshop.sales.RecentSaleSummary;

public record DashboardView(
        BigDecimal totalSales,
        BigDecimal outstandingCredit,
        BigDecimal paymentsReceived,
        int lowStockProductCount,
        List<RecentSaleSummary> recentSales,
        List<DashboardOutstandingCustomer> outstandingCustomers,
        List<ProductResponse> lowStockProducts,
        List<RecentPaymentSummary> recentPayments) {

    public DashboardView {
        recentSales = List.copyOf(recentSales);
        outstandingCustomers = List.copyOf(outstandingCustomers);
        lowStockProducts = List.copyOf(lowStockProducts);
        recentPayments = List.copyOf(recentPayments);
    }
}
