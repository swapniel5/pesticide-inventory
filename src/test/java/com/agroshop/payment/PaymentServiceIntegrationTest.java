package com.agroshop.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.agroshop.customer.Customer;
import com.agroshop.customer.CustomerFinancialSummary;
import com.agroshop.customer.CustomerRepository;
import com.agroshop.customer.CustomerService;
import com.agroshop.sales.Sale;
import com.agroshop.sales.SaleRepository;
import com.agroshop.sales.SaleStatus;
import com.agroshop.payment.PaymentAllocation;
import com.agroshop.payment.PaymentAllocationRepository;
import com.agroshop.payment.PaymentMethod;
import com.agroshop.payment.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:payments-integration;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class PaymentServiceIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoSpyBean
    private PaymentAllocationRepository paymentAllocationRepository;

    @Autowired
    private MockMvc mockMvc;

    private Customer customer;

    @BeforeEach
    void setUp() {
        customer = saveCustomer();
    }

    @Test
    void recordsTheFullOutstandingAmountAndUpdatesTheBalance() {
        Sale sale = createSale(customer, "300.00");
        createExistingPayment(customer, sale, "100.00");

        PaymentView payment = paymentService.recordPayment(
                manualForm(customer, "200.00", allocation(sale, "200.00")));

        assertThat(payment.amount()).isEqualByComparingTo("200.00");
        assertThat(payment.allocations()).singleElement()
                .extracting(PaymentInvoiceAllocation::allocatedAmount)
                .isEqualTo(new BigDecimal("200.00"));
        CustomerFinancialSummary summary = customerService.getCustomerProfile(customer.getId()).financialSummary();
        assertThat(summary.totalPurchases()).isEqualByComparingTo("300.00");
        assertThat(summary.totalPaymentsReceived()).isEqualByComparingTo("300.00");
        assertThat(summary.outstandingBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void recordsAPartialPayment() {
        Sale sale = createSale(customer, "300.00");

        paymentService.recordPayment(manualForm(customer, "120.00", allocation(sale, "120.00")));

        CustomerFinancialSummary summary = customerService.getCustomerProfile(customer.getId()).financialSummary();
        assertThat(summary.totalPaymentsReceived()).isEqualByComparingTo("120.00");
        assertThat(summary.outstandingBalance()).isEqualByComparingTo("180.00");
    }

    @Test
    void allocatesOnePaymentAcrossMultipleInvoices() {
        Sale oldest = createSale(customer, "100.00");
        Sale newer = createSale(customer, "200.00");

        PaymentView payment = paymentService.recordPayment(manualForm(
                customer, "250.00", allocation(oldest, "100.00"), allocation(newer, "150.00")));

        assertThat(payment.allocations()).hasSize(2);
        assertThat(payment.allocations()).extracting(PaymentInvoiceAllocation::allocatedAmount)
                .containsExactly(new BigDecimal("100.00"), new BigDecimal("150.00"));
    }

    @Test
    void automaticallyAllocatesToTheOldestOutstandingInvoicesFirst() {
        Sale oldest = createSale(customer, "100.00");
        Sale newer = createSale(customer, "200.00");
        PaymentForm form = automaticForm(customer, "150.00");
        form.getAllocations().get(0).setAmount(new BigDecimal("100.00"));
        form.getAllocations().get(1).setAmount(new BigDecimal("50.00"));

        PaymentView payment = paymentService.recordPayment(form);

        assertThat(payment.allocations()).extracting(PaymentInvoiceAllocation::saleId)
                .containsExactly(oldest.getId(), newer.getId());
        assertThat(payment.allocations()).extracting(PaymentInvoiceAllocation::allocatedAmount)
                .containsExactly(new BigDecimal("100.00"), new BigDecimal("50.00"));
    }

    @Test
    void rejectsPaymentGreaterThanCustomerOutstandingBalance() {
        Sale sale = createSale(customer, "100.00");
        long paymentsBefore = paymentRepository.count();

        assertThatThrownBy(() -> paymentService.recordPayment(
                manualForm(customer, "100.01", allocation(sale, "100.01"))))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("total outstanding balance");
        assertThat(paymentRepository.count()).isEqualTo(paymentsBefore);
    }

    @Test
    void rejectsAllocationGreaterThanInvoiceBalance() {
        Sale sale = createSale(customer, "100.00");
        createExistingPayment(customer, sale, "50.00");
        long paymentsBefore = paymentRepository.count();

        assertThatThrownBy(() -> paymentService.recordPayment(
                manualForm(customer, "50.00", allocation(sale, "50.01"))))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("cannot exceed the invoice");
        assertThat(paymentRepository.count()).isEqualTo(paymentsBefore);
    }

    @Test
    void rejectsAllocationsToAnotherCustomersInvoice() {
        Customer otherCustomer = saveCustomer();
        Sale ownSale = createSale(customer, "100.00");
        Sale otherSale = createSale(otherCustomer, "100.00");
        long paymentsBefore = paymentRepository.count();

        assertThatThrownBy(() -> paymentService.recordPayment(
                manualForm(customer, "50.00", allocation(otherSale, "50.00"))))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("belong to this customer");
        assertThat(paymentRepository.count()).isEqualTo(paymentsBefore);
        assertThat(ownSale.getId()).isNotEqualTo(otherSale.getId());
    }

    @Test
    void rejectsAllocationToFullyPaidInvoice() {
        Sale sale = createSale(customer, "100.00");
        createExistingPayment(customer, sale, "100.00");
        long paymentsBefore = paymentRepository.count();

        assertThatThrownBy(() -> paymentService.recordPayment(
                manualForm(customer, "1.00", allocation(sale, "1.00"))))
                .isInstanceOf(PaymentValidationException.class);
        assertThat(paymentRepository.count()).isEqualTo(paymentsBefore);
    }

    @Test
    void rollsBackPaymentAndEarlierAllocationsWhenAnAllocationSaveFails() {
        Sale oldest = createSale(customer, "100.00");
        Sale newer = createSale(customer, "100.00");
        long paymentCountBefore = paymentRepository.count();
        long allocationCountBefore = paymentAllocationRepository.count();
        doAnswer(invocation -> {
            throw new IllegalStateException("Simulated allocation persistence failure");
        }).when(paymentAllocationRepository).save(any(PaymentAllocation.class));

        assertThatThrownBy(() -> paymentService.recordPayment(manualForm(
                customer, "150.00", allocation(oldest, "100.00"), allocation(newer, "50.00"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Simulated allocation persistence failure");

        assertThat(paymentRepository.count()).isEqualTo(paymentCountBefore);
        assertThat(paymentAllocationRepository.count()).isEqualTo(allocationCountBefore);
    }

    @Test
    @WithMockUser
    void retrievesPaymentHistoryAndRendersPaymentPagesAndProfileSummary() throws Exception {
        Sale sale = createSale(customer, "300.00");
        PaymentView payment = paymentService.recordPayment(
                manualForm(customer, "125.00", allocation(sale, "125.00")));

        mockMvc.perform(get("/payments"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(payment.reference())));
        mockMvc.perform(get("/payments")
                        .param("customerId", customer.getId().toString())
                        .param("fromDate", LocalDate.now().toString())
                        .param("toDate", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(payment.reference())))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(customer.getName())));
        mockMvc.perform(get("/payments/" + payment.id()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(sale.getInvoiceNumber())))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("₹125.00")));
        mockMvc.perform(get("/customers/" + customer.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Record payment")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("₹175.00")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(payment.reference())));
        mockMvc.perform(get("/customers/" + customer.getId() + "/payments/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Outstanding invoices")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(sale.getInvoiceNumber())));
    }

    @Test
    @WithMockUser
    void recordsPaymentThroughTheThymeleafForm() throws Exception {
        Sale sale = createSale(customer, "100.00");

        mockMvc.perform(post("/payments")
                        .with(csrf())
                        .param("customerId", customer.getId().toString())
                        .param("amount", "40.00")
                        .param("paymentDate", "2026-11-14T20:00")
                        .param("paymentMethod", "CASH")
                        .param("transactionReference", "CASH-REC-1")
                        .param("notes", "Farm visit")
                        .param("requestKey", UUID.randomUUID().toString())
                        .param("allocations[0].saleId", sale.getId().toString())
                        .param("allocations[0].amount", "40.00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/payments/")));

        assertThat(paymentService.findCustomerPayments(customer.getId()))
                .anySatisfy(payment -> {
                    assertThat(payment.transactionReference()).isEqualTo("CASH-REC-1");
                    assertThat(payment.amount()).isEqualByComparingTo("40.00");
                });
    }

    @Test
    void preventsConcurrentPaymentsFromExceedingInvoiceBalance() throws Exception {
        Sale sale = createSale(customer, "100.00");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> first = executor.submit(() -> tryPayment(customer, sale, ready, start));
            Future<Boolean> second = executor.submit(() -> tryPayment(customer, sale, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Boolean> results = new ArrayList<>(List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)));

            assertThat(results).containsExactlyInAnyOrder(true, false);
            assertThat(paymentAllocationRepository.sumValidAllocatedPaymentsForSale(
                    sale.getId(), SaleStatus.COMPLETED))
                    .isEqualByComparingTo("80.00");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void repeatedSubmissionKeyReturnsTheExistingPaymentWithoutDuplicatingAllocations() {
        Sale sale = createSale(customer, "100.00");
        PaymentForm form = manualForm(customer, "50.00", allocation(sale, "50.00"));
        long paymentCountBefore = paymentRepository.count();

        PaymentView first = paymentService.recordPayment(form);
        PaymentView repeated = paymentService.recordPayment(form);

        assertThat(repeated.id()).isEqualTo(first.id());
        assertThat(paymentRepository.count()).isEqualTo(paymentCountBefore + 1);
        assertThat(paymentAllocationRepository.sumValidAllocatedPaymentsForSale(
                sale.getId(), SaleStatus.COMPLETED))
                .isEqualByComparingTo("50.00");
    }

    private boolean tryPayment(
            Customer paymentCustomer,
            Sale sale,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        PaymentForm form = automaticForm(paymentCustomer, "80.00");
        form.getAllocations().forEach(allocation -> allocation.setAmount(new BigDecimal("80.00")));
        ready.countDown();
        start.await();
        try {
            paymentService.recordPayment(form);
            return true;
        } catch (PaymentValidationException exception) {
            return false;
        }
    }

    private Customer saveCustomer() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return customerRepository.save(new Customer(
                "Farmer " + suffix,
                "9" + suffix,
                "Rampur",
                null,
                null));
    }

    private Sale createSale(Customer saleCustomer, String amount) {
        String suffix = UUID.randomUUID().toString();
        return saleRepository.save(new Sale(
                saleCustomer,
                new BigDecimal(amount),
                "payment-test-" + suffix,
                "INV-PAY-" + suffix));
    }

    private void createExistingPayment(Customer paymentCustomer, Sale sale, String amount) {
        BigDecimal value = new BigDecimal(amount);
        Payment payment = paymentRepository.save(new Payment(paymentCustomer, value, PaymentMethod.CASH, null));
        paymentAllocationRepository.save(new PaymentAllocation(payment, sale, value));
    }

    private PaymentForm manualForm(Customer formCustomer, String amount, PaymentAllocationForm... allocations) {
        PaymentForm form = baseForm(formCustomer, amount);
        form.setAllocations(List.of(allocations));
        return form;
    }

    private PaymentForm automaticForm(Customer formCustomer, String amount) {
        PaymentForm form = baseForm(formCustomer, amount);
        form.setAutoAllocate(true);
        List<PaymentAllocationForm> allocations = new ArrayList<>();
        for (OutstandingInvoice invoice : paymentService.findOutstandingInvoices(formCustomer.getId())) {
            allocations.add(allocation(invoice.saleId(), "0.00"));
        }
        form.setAllocations(allocations);
        return form;
    }

    private PaymentForm baseForm(Customer formCustomer, String amount) {
        PaymentForm form = new PaymentForm();
        form.setCustomerId(formCustomer.getId());
        form.setAmount(new BigDecimal(amount));
        form.setPaymentDate(LocalDateTime.now().withSecond(0).withNano(0));
        form.setPaymentMethod(PaymentMethod.UPI);
        form.setTransactionReference("TXN-" + UUID.randomUUID());
        form.setNotes("Test payment");
        form.setRequestKey(UUID.randomUUID().toString());
        return form;
    }

    private PaymentAllocationForm allocation(Sale sale, String amount) {
        return allocation(sale.getId(), amount);
    }

    private PaymentAllocationForm allocation(Long saleId, String amount) {
        PaymentAllocationForm allocation = new PaymentAllocationForm();
        allocation.setSaleId(saleId);
        allocation.setAmount(new BigDecimal(amount));
        return allocation;
    }
}
