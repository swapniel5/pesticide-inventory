package com.agroshop.dashboard;

import java.util.List;

import com.agroshop.customer.CustomerOutstandingSummary;
import com.agroshop.customer.CustomerRepository;
import com.agroshop.customer.CustomerFinancialSummary;
import com.agroshop.payment.PaymentAllocationRepository;
import com.agroshop.payment.PaymentRepository;
import com.agroshop.product.ProductResponse;
import com.agroshop.product.ProductService;
import com.agroshop.sales.SaleRepository;
import com.agroshop.sales.SaleStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private static final int RECENT_LIMIT = 10;

    private final SaleRepository saleRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final PaymentRepository paymentRepository;
    private final CustomerRepository customerRepository;
    private final ProductService productService;

    public DashboardService(
            SaleRepository saleRepository,
            PaymentAllocationRepository paymentAllocationRepository,
            PaymentRepository paymentRepository,
            CustomerRepository customerRepository,
            ProductService productService) {
        this.saleRepository = saleRepository;
        this.paymentAllocationRepository = paymentAllocationRepository;
        this.paymentRepository = paymentRepository;
        this.customerRepository = customerRepository;
        this.productService = productService;
    }

    @Transactional(readOnly = true)
    public DashboardView getDashboard() {
        CustomerFinancialSummary financialSummary = CustomerFinancialSummary.from(
                saleRepository.sumCompletedSales(SaleStatus.COMPLETED),
                paymentAllocationRepository.sumValidAllocatedPayments(SaleStatus.COMPLETED));
        List<ProductResponse> lowStockProducts = productService.findLowStockProducts().stream()
                .map(ProductResponse::from)
                .toList();
        List<DashboardOutstandingCustomer> outstandingCustomers =
                customerRepository.findTopOutstandingBalances(SaleStatus.COMPLETED.name()).stream()
                        .map(this::toDashboardCustomer)
                        .toList();

        return new DashboardView(
                financialSummary.totalPurchases(),
                financialSummary.outstandingBalance(),
                financialSummary.totalPaymentsReceived(),
                lowStockProducts.size(),
                saleRepository.findRecentCompletedSales(
                        SaleStatus.COMPLETED, PageRequest.of(0, RECENT_LIMIT)),
                outstandingCustomers,
                lowStockProducts,
                paymentRepository.findRecentPayments(PageRequest.of(0, RECENT_LIMIT)));
    }

    private DashboardOutstandingCustomer toDashboardCustomer(CustomerOutstandingSummary summary) {
        return new DashboardOutstandingCustomer(
                summary.getCustomerId(),
                summary.getCustomerName(),
                summary.getPhone(),
                summary.getVillage(),
                summary.getOutstandingBalance(),
                summary.isActive());
    }
}
