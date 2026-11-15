package com.agroshop.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import com.agroshop.payment.Payment;
import com.agroshop.payment.PaymentAllocation;
import com.agroshop.payment.PaymentAllocationRepository;
import com.agroshop.payment.PaymentMethod;
import com.agroshop.payment.PaymentRepository;
import com.agroshop.sales.Sale;
import com.agroshop.sales.SaleRepository;
import com.agroshop.sales.SaleService;
import com.agroshop.sales.SaleStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:customer-sales-profile;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class CustomerSalesProfileIntegrationTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private SaleService saleService;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentAllocationRepository paymentAllocationRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MockMvc mockMvc;

    private Customer customer;

    @BeforeEach
    void setUp() {
        customer = customerRepository.save(new Customer(
                "Farmer " + UUID.randomUUID(),
                "9876543210",
                "Rampur",
                null,
                null));
    }

    @Test
    void customerWithoutPurchasesHasAnEmptyHistoryAndZeroSummary() {
        CustomerProfileResponse profile = customerService.getCustomerProfile(customer.getId());

        assertThat(profile.purchaseHistory()).isEmpty();
        assertThat(profile.financialSummary().totalPurchases()).isEqualByComparingTo("0.00");
        assertThat(profile.financialSummary().totalPaymentsReceived()).isEqualByComparingTo("0.00");
        assertThat(profile.financialSummary().outstandingBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    @WithMockUser
    void showsTheExpectedPartiallyPaidInvoiceAndLinksToItsSale() throws Exception {
        Sale sale = createSale("INV-2026-0003", "1300.00");
        allocate(sale, customer, "1000.00");

        CustomerProfileResponse profile = customerService.getCustomerProfile(customer.getId());

        assertThat(profile.purchaseHistory()).hasSize(1);
        assertThat(profile.purchaseHistory().getFirst().amountPaid()).isEqualByComparingTo("1000.00");
        assertThat(profile.purchaseHistory().getFirst().outstandingAmount()).isEqualByComparingTo("300.00");
        assertThat(profile.purchaseHistory().getFirst().getPaymentStatusLabel()).isEqualTo("PARTIALLY PAID");
        assertThat(profile.financialSummary().totalPurchases()).isEqualByComparingTo("1300.00");
        assertThat(profile.financialSummary().totalPaymentsReceived()).isEqualByComparingTo("1000.00");
        assertThat(profile.financialSummary().outstandingBalance()).isEqualByComparingTo("300.00");

        mockMvc.perform(get("/customers/" + customer.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("INV-2026-0003")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/sales/" + sale.getId())))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("₹1,300.00")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("₹1,000.00")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("₹300.00")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("PARTIALLY PAID")));
    }

    @Test
    void calculatesPartialPaymentOutstandingBalance() {
        Sale sale = createSale("INV-PARTIAL-" + UUID.randomUUID(), "500.00");
        allocate(sale, customer, "125.00");

        CustomerFinancialSummary summary = customerService.getCustomerProfile(customer.getId()).financialSummary();

        assertThat(summary.totalPurchases()).isEqualByComparingTo("500.00");
        assertThat(summary.totalPaymentsReceived()).isEqualByComparingTo("125.00");
        assertThat(summary.outstandingBalance()).isEqualByComparingTo("375.00");
    }

    @Test
    void sumsMultipleSalesAndSortsHistoryNewestFirst() {
        createSale("INV-OLD-" + UUID.randomUUID(), "100.00");
        createSale("INV-NEW-" + UUID.randomUUID(), "200.00");

        CustomerProfileResponse profile = customerService.getCustomerProfile(customer.getId());

        assertThat(profile.purchaseHistory()).hasSize(2);
        assertThat(profile.purchaseHistory().getFirst().invoiceNumber()).startsWith("INV-NEW-");
        assertThat(profile.financialSummary().totalPurchases()).isEqualByComparingTo("300.00");
        assertThat(profile.financialSummary().outstandingBalance()).isEqualByComparingTo("300.00");
    }

    @Test
    void sumsMultiplePaymentAllocationsWithoutCountingPaymentAmountsTwice() {
        Sale sale = createSale("INV-MULTIPAY-" + UUID.randomUUID(), "1000.00");
        allocate(sale, customer, "200.00");
        allocate(sale, customer, "300.00");

        CustomerProfileResponse profile = customerService.getCustomerProfile(customer.getId());

        assertThat(profile.purchaseHistory().getFirst().amountPaid()).isEqualByComparingTo("500.00");
        assertThat(profile.financialSummary().totalPaymentsReceived()).isEqualByComparingTo("500.00");
        assertThat(profile.financialSummary().outstandingBalance()).isEqualByComparingTo("500.00");
    }

    @Test
    void excludesCancelledSalesAndTheirAllocationsFromFinancialTotals() {
        Sale completed = createSale("INV-COMPLETE-" + UUID.randomUUID(), "100.00");
        Sale cancelled = createSale("INV-CANCELLED-" + UUID.randomUUID(), "300.00");
        allocate(completed, customer, "30.00");
        allocate(cancelled, customer, "300.00");
        entityManager.flush();
        entityManager.createNativeQuery("update sales set status = 'CANCELLED' where id = :saleId")
                .setParameter("saleId", cancelled.getId())
                .executeUpdate();
        entityManager.clear();

        CustomerProfileResponse profile = customerService.getCustomerProfile(customer.getId());

        assertThat(profile.purchaseHistory()).hasSize(2);
        CustomerPurchaseHistoryItem cancelledPurchase = profile.purchaseHistory().stream()
                .filter(item -> item.status() == SaleStatus.CANCELLED)
                .findFirst()
                .orElseThrow();
        assertThat(cancelledPurchase.amountPaid()).isEqualByComparingTo("0.00");
        assertThat(cancelledPurchase.outstandingAmount()).isEqualByComparingTo("0.00");
        assertThat(cancelledPurchase.getPaymentStatusLabel()).isEqualTo("CANCELLED");
        var cancelledSaleResponse = saleService.getSale(cancelled.getId());
        assertThat(cancelledSaleResponse.amountPaid()).isEqualByComparingTo("0.00");
        assertThat(cancelledSaleResponse.outstandingBalance()).isEqualByComparingTo("0.00");
        assertThat(cancelledSaleResponse.getPaymentStatusLabel()).isEqualTo("CANCELLED");
        assertThat(profile.financialSummary().totalPurchases()).isEqualByComparingTo("100.00");
        assertThat(profile.financialSummary().totalPaymentsReceived()).isEqualByComparingTo("30.00");
        assertThat(profile.financialSummary().outstandingBalance()).isEqualByComparingTo("70.00");
    }

    @Test
    void excludesAllocationsWhosePaymentBelongsToAnotherCustomer() {
        Customer otherCustomer = customerRepository.save(new Customer(
                "Other farmer " + UUID.randomUUID(),
                "9123456789",
                "Sundarpur",
                null,
                null));
        Sale sale = createSale("INV-WRONG-OWNER-" + UUID.randomUUID(), "200.00");
        allocate(sale, otherCustomer, "100.00");

        CustomerProfileResponse profile = customerService.getCustomerProfile(customer.getId());
        CustomerProfileResponse otherProfile = customerService.getCustomerProfile(otherCustomer.getId());

        assertThat(profile.financialSummary().totalPaymentsReceived()).isEqualByComparingTo("0.00");
        assertThat(profile.financialSummary().outstandingBalance()).isEqualByComparingTo("200.00");
        assertThat(otherProfile.financialSummary().totalPurchases()).isEqualByComparingTo("0.00");
        assertThat(otherProfile.financialSummary().totalPaymentsReceived()).isEqualByComparingTo("0.00");
    }

    @Test
    void keepsEachCustomersSalesAndPaymentsIsolated() {
        Customer otherCustomer = customerRepository.save(new Customer(
                "Other farmer " + UUID.randomUUID(),
                "9123456789",
                "Sundarpur",
                null,
                null));
        Sale firstSale = createSale("INV-FIRST-" + UUID.randomUUID(), "100.00");
        Sale otherSale = createSale(otherCustomer, "INV-OTHER-" + UUID.randomUUID(), "500.00");
        allocate(firstSale, customer, "40.00");
        allocate(otherSale, otherCustomer, "200.00");

        CustomerFinancialSummary firstSummary =
                customerService.getCustomerProfile(customer.getId()).financialSummary();
        CustomerFinancialSummary otherSummary =
                customerService.getCustomerProfile(otherCustomer.getId()).financialSummary();

        assertThat(firstSummary.totalPurchases()).isEqualByComparingTo("100.00");
        assertThat(firstSummary.totalPaymentsReceived()).isEqualByComparingTo("40.00");
        assertThat(firstSummary.outstandingBalance()).isEqualByComparingTo("60.00");
        assertThat(otherSummary.totalPurchases()).isEqualByComparingTo("500.00");
        assertThat(otherSummary.totalPaymentsReceived()).isEqualByComparingTo("200.00");
        assertThat(otherSummary.outstandingBalance()).isEqualByComparingTo("300.00");
    }

    private Sale createSale(String invoiceNumber, String totalAmount) {
        return createSale(customer, invoiceNumber, totalAmount);
    }

    private Sale createSale(Customer owner, String invoiceNumber, String totalAmount) {
        Sale sale = new Sale(
                owner,
                new BigDecimal(totalAmount),
                "profile-" + UUID.randomUUID(),
                invoiceNumber);
        return saleRepository.save(sale);
    }

    private void allocate(Sale sale, Customer paymentCustomer, String amount) {
        BigDecimal allocatedAmount = new BigDecimal(amount);
        Payment payment = paymentRepository.save(new Payment(
                paymentCustomer,
                allocatedAmount,
                PaymentMethod.CASH,
                "Profile integration test"));
        paymentAllocationRepository.save(new PaymentAllocation(payment, sale, allocatedAmount));
    }
}
