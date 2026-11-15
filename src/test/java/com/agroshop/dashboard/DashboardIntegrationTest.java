package com.agroshop.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.UUID;

import com.agroshop.customer.Customer;
import com.agroshop.customer.CustomerRepository;
import com.agroshop.customer.CustomerService;
import com.agroshop.payment.Payment;
import com.agroshop.payment.PaymentAllocation;
import com.agroshop.payment.PaymentAllocationRepository;
import com.agroshop.payment.PaymentMethod;
import com.agroshop.payment.PaymentRepository;
import com.agroshop.product.Product;
import com.agroshop.product.ProductRepository;
import com.agroshop.sales.Sale;
import com.agroshop.sales.SaleRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:dashboard-integration;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class DashboardIntegrationTest {

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentAllocationRepository paymentAllocationRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void summarizesCompletedSalesValidAllocationsAndActiveLowStockProducts() {
        Customer customer = createCustomer("Mohan");
        Sale completedSale = createSale(customer, "INV-DASH-COMPLETE", "1000.00");
        allocate(completedSale, customer, "300.00", LocalDateTime.now(), "PAY-DASH-300");
        Sale cancelledSale = createSale(customer, "INV-DASH-CANCELLED", "400.00");
        allocate(cancelledSale, customer, "400.00", LocalDateTime.now(), "PAY-DASH-400");
        entityManager.flush();
        entityManager.createNativeQuery("update sales set status = 'CANCELLED' where id = :saleId")
                .setParameter("saleId", cancelledSale.getId())
                .executeUpdate();
        entityManager.clear();

        productRepository.save(new Product(
                "At threshold", "Brand", "bottle", new BigDecimal("5.000"),
                new BigDecimal("20.00"), new BigDecimal("5.000")));
        Product inactiveLowStock = new Product(
                "Inactive low stock", "Brand", "bottle", BigDecimal.ZERO,
                new BigDecimal("20.00"), BigDecimal.ONE);
        inactiveLowStock.deactivate();
        productRepository.save(inactiveLowStock);
        productRepository.save(new Product(
                "Healthy stock", "Brand", "bottle", new BigDecimal("6.000"),
                new BigDecimal("20.00"), new BigDecimal("5.000")));

        DashboardView dashboard = dashboardService.getDashboard();

        assertThat(dashboard.totalSales()).isEqualByComparingTo("1000.00");
        assertThat(dashboard.paymentsReceived()).isEqualByComparingTo("300.00");
        assertThat(dashboard.outstandingCredit()).isEqualByComparingTo("700.00");
        assertThat(dashboard.lowStockProductCount()).isEqualTo(1);
        assertThat(dashboard.lowStockProducts()).extracting("name").containsExactly("At threshold");
    }

    @Test
    void returnsTheTenNewestCompletedSalesInDescendingOrder() {
        Customer customer = createCustomer("Recent sales farmer");
        for (int index = 1; index <= 12; index++) {
            createSale(customer, "INV-DASH-SALE-" + index, "100.00");
        }
        Sale cancelledSale = createSale(customer, "INV-DASH-SALE-CANCELLED", "900.00");
        entityManager.flush();
        entityManager.createNativeQuery("update sales set status = 'CANCELLED' where id = :saleId")
                .setParameter("saleId", cancelledSale.getId())
                .executeUpdate();
        entityManager.clear();

        var recentSales = dashboardService.getDashboard().recentSales();

        assertThat(recentSales).hasSize(10);
        assertThat(recentSales.getFirst().invoiceNumber()).isEqualTo("INV-DASH-SALE-12");
        assertThat(recentSales.getLast().invoiceNumber()).isEqualTo("INV-DASH-SALE-3");
        assertThat(recentSales).allMatch(sale -> sale.status().name().equals("COMPLETED"));
    }

    @Test
    void returnsTheTenMostRecentPaymentsInDescendingDateOrder() {
        Customer customer = createCustomer("Recent payments farmer");
        Sale sale = createSale(customer, "INV-DASH-PAYMENTS", "1000.00");
        for (int index = 1; index <= 12; index++) {
            allocate(
                    sale,
                    customer,
                    "1.00",
                    LocalDateTime.of(2026, 1, index, 10, 0),
                    "PAY-DASH-" + index);
        }

        var recentPayments = dashboardService.getDashboard().recentPayments();

        assertThat(recentPayments).hasSize(10);
        assertThat(recentPayments.getFirst().reference()).isEqualTo("PAY-DASH-12");
        assertThat(recentPayments.getLast().reference()).isEqualTo("PAY-DASH-3");
    }

    @Test
    void listsOnlyFiveCustomersWithTheHighestPositiveOutstandingBalances() {
        Customer inactiveDebtor = createCustomer("Inactive debtor");
        inactiveDebtor.deactivate();
        customerRepository.save(inactiveDebtor);
        createSale(inactiveDebtor, "INV-DASH-INACTIVE", "700.00");
        for (int index = 1; index <= 6; index++) {
            Customer customer = createCustomer("Debtor " + index);
            createSale(customer, "INV-DASH-DEBTOR-" + index, (index * 100) + ".00");
        }
        Customer paidCustomer = createCustomer("Paid customer");
        Sale paidSale = createSale(paidCustomer, "INV-DASH-PAID", "800.00");
        allocate(paidSale, paidCustomer, "800.00", LocalDateTime.now(), "PAY-DASH-FULL");

        var outstandingCustomers = dashboardService.getDashboard().outstandingCustomers();

        assertThat(outstandingCustomers).hasSize(5);
        assertThat(outstandingCustomers).extracting(DashboardOutstandingCustomer::outstandingBalance)
                .containsExactly(
                        new BigDecimal("700.00"),
                        new BigDecimal("600.00"),
                        new BigDecimal("500.00"),
                        new BigDecimal("400.00"),
                        new BigDecimal("300.00"));
        assertThat(outstandingCustomers.getFirst().customerName()).startsWith("Inactive debtor");
        assertThat(outstandingCustomers.getFirst().active()).isFalse();
    }

    @Test
    @WithMockUser
    void returnsZeroValuesAndRendersHelpfulEmptyStatesWhenThereIsNoActivity() throws Exception {
        DashboardView dashboard = dashboardService.getDashboard();

        assertThat(dashboard.totalSales()).isEqualByComparingTo("0.00");
        assertThat(dashboard.outstandingCredit()).isEqualByComparingTo("0.00");
        assertThat(dashboard.paymentsReceived()).isEqualByComparingTo("0.00");
        assertThat(dashboard.lowStockProductCount()).isZero();
        assertThat(dashboard.recentSales()).isEmpty();
        assertThat(dashboard.recentPayments()).isEmpty();
        assertThat(dashboard.outstandingCustomers()).isEmpty();
        assertThat(dashboard.lowStockProducts()).isEmpty();

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Shop dashboard")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("No completed sales yet")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("No payments recorded yet")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Crafted by Swapnil Jejurkar")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        String.valueOf(Year.now().getValue()))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "All Rights Reserved. Created by Swapnil Jejurkar.")));
    }

    @Test
    void dashboardFinancialTotalsMatchTheCustomerFinancialSummary() {
        Customer customer = createCustomer("Summary consistency");
        Sale sale = createSale(customer, "INV-DASH-CONSISTENCY", "1300.00");
        allocate(sale, customer, "1000.00", LocalDateTime.now(), "PAY-DASH-CONSISTENCY");
        Customer otherCustomer = createCustomer("Second summary customer");
        createSale(otherCustomer, "INV-DASH-SECOND-CONSISTENCY", "700.00");

        DashboardView dashboard = dashboardService.getDashboard();
        var customerSummary = customerService.getCustomerProfile(customer.getId()).financialSummary();
        var otherCustomerSummary = customerService.getCustomerProfile(otherCustomer.getId()).financialSummary();

        assertThat(dashboard.totalSales()).isEqualByComparingTo(
                customerSummary.totalPurchases().add(otherCustomerSummary.totalPurchases()));
        assertThat(dashboard.paymentsReceived()).isEqualByComparingTo(
                customerSummary.totalPaymentsReceived().add(otherCustomerSummary.totalPaymentsReceived()));
        assertThat(dashboard.outstandingCredit()).isEqualByComparingTo(
                customerSummary.outstandingBalance().add(otherCustomerSummary.outstandingBalance()));
        var recentSale = dashboard.recentSales().stream()
                .filter(item -> item.saleId().equals(sale.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(recentSale.amountPaid()).isEqualByComparingTo("1000.00");
        assertThat(recentSale.outstandingBalance()).isEqualByComparingTo("300.00");
        assertThat(recentSale.getPaymentStatusLabel()).isEqualTo("PARTIALLY PAID");
    }

    @Test
    @WithMockUser
    void dashboardProvidesQuickActionsAndPaymentCustomerSelection() throws Exception {
        Customer customer = createCustomer("Payment chooser farmer");

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/sales/new")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/products/new")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/customers/new")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/payments/new")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/inventory")));

        mockMvc.perform(get("/payments/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Payment chooser farmer")));

        mockMvc.perform(get("/payments/new").param("customerId", customer.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customers/" + customer.getId() + "/payments/new"));
    }

    private Customer createCustomer(String name) {
        return customerRepository.save(new Customer(
                name + " " + UUID.randomUUID().toString().substring(0, 8),
                "9876543210",
                "Rampur",
                null,
                null));
    }

    private Sale createSale(Customer customer, String invoiceNumber, String amount) {
        return saleRepository.save(new Sale(
                customer,
                new BigDecimal(amount),
                "dashboard-" + UUID.randomUUID(),
                invoiceNumber));
    }

    private void allocate(
            Sale sale,
            Customer customer,
            String amount,
            LocalDateTime date,
            String reference) {
        BigDecimal value = new BigDecimal(amount);
        Payment payment = paymentRepository.save(new Payment(
                customer,
                value,
                PaymentMethod.CASH,
                date,
                reference,
                "Dashboard integration test",
                "dashboard-payment-" + UUID.randomUUID()));
        paymentAllocationRepository.save(new PaymentAllocation(payment, sale, value));
    }
}
