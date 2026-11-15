package com.agroshop.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.net.URI;
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
import com.agroshop.customer.CustomerPurchaseHistoryItem;
import com.agroshop.customer.CustomerProfileResponse;
import com.agroshop.customer.CustomerRepository;
import com.agroshop.customer.CustomerService;
import com.agroshop.dashboard.DashboardService;
import com.agroshop.dashboard.DashboardView;
import com.agroshop.inventory.InsufficientStockException;
import com.agroshop.inventory.MovementType;
import com.agroshop.inventory.StockMovement;
import com.agroshop.inventory.StockMovementRepository;
import com.agroshop.payment.Payment;
import com.agroshop.payment.PaymentAllocation;
import com.agroshop.payment.PaymentAllocationForm;
import com.agroshop.payment.PaymentAllocationRepository;
import com.agroshop.payment.PaymentForm;
import com.agroshop.payment.PaymentMethod;
import com.agroshop.payment.PaymentRepository;
import com.agroshop.payment.PaymentService;
import com.agroshop.product.Product;
import com.agroshop.product.ProductForm;
import com.agroshop.product.ProductRepository;
import com.agroshop.product.ProductService;
import com.agroshop.receipt.ReceiptService;
import com.agroshop.receipt.ReceiptView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sales-integration;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class SaleServiceIntegrationTest {

    @Autowired
    private SaleService saleService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductService productService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private ReceiptService receiptService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentAllocationRepository paymentAllocationRepository;

    @Autowired
    private MockMvc mockMvc;

    private Customer customer;
    private Product productA;
    private Product productB;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        customer = customerRepository.save(new Customer("Farmer " + suffix, "9876543210", "Rampur", null, null));
        productA = saveProduct("Spray A " + suffix, "10.00", "10");
        productB = saveProduct("Spray B " + suffix, "2.00", "5");
    }

    @Test
    void completesMultiProductSaleWithFullPaymentAndDeductsStock() {
        long allocationsBefore = paymentAllocationRepository.count();
        long movementsBeforeA = saleMovementCount(productA.getId());
        long movementsBeforeB = saleMovementCount(productB.getId());

        SaleCreationResult result = saleService.createSale(request(
                UUID.randomUUID().toString(),
                "23.00",
                PaymentMethod.CASH,
                line(productA, "2"),
                line(productB, "1.5")));

        assertThat(result.created()).isTrue();
        assertThat(result.sale().invoiceNumber()).matches("INV-\\d{4}-\\d{4,}");
        assertThat(result.sale().totalAmount()).isEqualByComparingTo("23.00");
        assertThat(result.sale().amountPaid()).isEqualByComparingTo("23.00");
        assertThat(result.sale().outstandingBalance()).isEqualByComparingTo("0.00");
        assertThat(result.sale().getPaymentStatusLabel()).isEqualTo("PAID");
        assertThat(result.sale().items()).hasSize(2);
        assertThat(productQuantity(productA)).isEqualByComparingTo("8.000");
        assertThat(productQuantity(productB)).isEqualByComparingTo("3.500");
        assertThat(saleMovementCount(productA.getId())).isEqualTo(movementsBeforeA + 1);
        assertThat(saleMovementCount(productB.getId())).isEqualTo(movementsBeforeB + 1);
        assertThat(paymentAllocationRepository.count()).isEqualTo(allocationsBefore + 1);
        assertThat(saleMovements(productA.getId())).extracting(StockMovement::getQuantity)
                .contains(new BigDecimal("2.000"));
    }

    @Test
    void recordsPartialPaymentAndOutstandingBalance() {
        SaleResponse sale = saleService.createSale(request(
                UUID.randomUUID().toString(), "5.00", PaymentMethod.UPI, line(productA, "2"))).sale();

        assertThat(sale.totalAmount()).isEqualByComparingTo("20.00");
        assertThat(sale.amountPaid()).isEqualByComparingTo("5.00");
        assertThat(sale.outstandingBalance()).isEqualByComparingTo("15.00");
        assertThat(sale.getPaymentStatusLabel()).isEqualTo("PARTIALLY PAID");
    }

    @Test
    void zeroPaymentCreatesNoPaymentOrAllocation() {
        long paymentsBefore = paymentRepository.count();
        long allocationsBefore = paymentAllocationRepository.count();

        SaleResponse sale = saleService.createSale(request(
                UUID.randomUUID().toString(), "0.00", null, line(productA, "1"))).sale();

        assertThat(sale.amountPaid()).isEqualByComparingTo("0.00");
        assertThat(sale.outstandingBalance()).isEqualByComparingTo("10.00");
        assertThat(sale.getPaymentStatusLabel()).isEqualTo("UNPAID");
        assertThat(paymentRepository.count()).isEqualTo(paymentsBefore);
        assertThat(paymentAllocationRepository.count()).isEqualTo(allocationsBefore);
    }

    @Test
    @WithMockUser
    void reconcilesInitialAndSubsequentPaymentsAcrossAllFinancialViews() throws Exception {
        DashboardView beforeDashboard = dashboardService.getDashboard();
        SaleResponse partiallyPaid = saleService.createSale(request(
                UUID.randomUUID().toString(), "5.00", PaymentMethod.CASH, line(productA, "2"))).sale();
        SaleResponse unpaid = saleService.createSale(request(
                UUID.randomUUID().toString(), "0.00", null, line(productA, "1"))).sale();
        SaleResponse paid = saleService.createSale(request(
                UUID.randomUUID().toString(), "2.00", PaymentMethod.CASH, line(productB, "1"))).sale();

        Customer otherCustomer = customerRepository.save(new Customer(
                "Other farmer " + UUID.randomUUID(), "9123456789", "Rampur", null, null));
        SaleRequest otherSaleRequest = request(
                UUID.randomUUID().toString(), "0.00", null, line(productB, "1"));
        otherSaleRequest.setCustomerId(otherCustomer.getId());
        SaleResponse otherUnpaid = saleService.createSale(otherSaleRequest).sale();

        PaymentForm subsequentPayment = paymentForm(customer, partiallyPaid.id(), "7.00");
        paymentService.recordPayment(subsequentPayment);

        Payment invalidCrossCustomerPayment = paymentRepository.save(new Payment(
                otherCustomer, new BigDecimal("3.00"), PaymentMethod.CASH, "Invalid test allocation"));
        paymentAllocationRepository.save(new PaymentAllocation(
                invalidCrossCustomerPayment,
                saleRepository.findById(partiallyPaid.id()).orElseThrow(),
                new BigDecimal("3.00")));

        SaleResponse saleDetails = saleService.getSale(partiallyPaid.id());
        assertThat(saleDetails.totalAmount()).isEqualByComparingTo("20.00");
        assertThat(saleDetails.amountPaid()).isEqualByComparingTo("12.00");
        assertThat(saleDetails.outstandingBalance()).isEqualByComparingTo("8.00");
        assertThat(saleDetails.getPaymentStatusLabel()).isEqualTo("PARTIALLY PAID");
        assertThat(saleService.getSale(unpaid.id()).getPaymentStatusLabel()).isEqualTo("UNPAID");
        assertThat(saleService.getSale(paid.id()).getPaymentStatusLabel()).isEqualTo("PAID");
        assertThat(saleService.getSale(otherUnpaid.id()).getPaymentStatusLabel()).isEqualTo("UNPAID");

        CustomerProfileResponse customerProfile = customerService.getCustomerProfile(customer.getId());
        assertThat(customerProfile.financialSummary().totalPurchases()).isEqualByComparingTo("32.00");
        assertThat(customerProfile.financialSummary().totalPaymentsReceived()).isEqualByComparingTo("14.00");
        assertThat(customerProfile.financialSummary().outstandingBalance()).isEqualByComparingTo("18.00");
        assertThat(customerProfile.purchaseHistory().stream()
                .map(item -> item.outstandingAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("18.00");
        assertThat(historyFor(customerProfile, partiallyPaid.id()).amountPaid()).isEqualByComparingTo("12.00");
        assertThat(historyFor(customerProfile, partiallyPaid.id()).getPaymentStatusLabel())
                .isEqualTo("PARTIALLY PAID");
        assertThat(historyFor(customerProfile, unpaid.id()).getPaymentStatusLabel()).isEqualTo("UNPAID");
        assertThat(historyFor(customerProfile, paid.id()).getPaymentStatusLabel()).isEqualTo("PAID");

        CustomerProfileResponse otherProfile = customerService.getCustomerProfile(otherCustomer.getId());
        assertThat(otherProfile.financialSummary().outstandingBalance()).isEqualByComparingTo("2.00");

        DashboardView dashboard = dashboardService.getDashboard();
        assertThat(dashboard.totalSales().subtract(beforeDashboard.totalSales())).isEqualByComparingTo("34.00");
        assertThat(dashboard.paymentsReceived().subtract(beforeDashboard.paymentsReceived()))
                .isEqualByComparingTo("14.00");
        assertThat(dashboard.outstandingCredit().subtract(beforeDashboard.outstandingCredit()))
                .isEqualByComparingTo("20.00");
        assertThat(dashboard.outstandingCredit().subtract(beforeDashboard.outstandingCredit())).isEqualByComparingTo(
                customerProfile.financialSummary().outstandingBalance()
                        .add(otherProfile.financialSummary().outstandingBalance()));
        assertThat(recentSaleFor(dashboard, partiallyPaid.id()).amountPaid()).isEqualByComparingTo("12.00");
        assertThat(recentSaleFor(dashboard, partiallyPaid.id()).outstandingBalance()).isEqualByComparingTo("8.00");
        assertThat(recentSaleFor(dashboard, partiallyPaid.id()).getPaymentStatusLabel())
                .isEqualTo("PARTIALLY PAID");
        assertThat(recentSaleFor(dashboard, unpaid.id()).getPaymentStatusLabel()).isEqualTo("UNPAID");
        assertThat(recentSaleFor(dashboard, paid.id()).getPaymentStatusLabel()).isEqualTo("PAID");

        ReceiptView receipt = receiptService.getReceipt(partiallyPaid.id());
        assertThat(receipt.totalPaid()).isEqualByComparingTo("12.00");
        assertThat(receipt.outstandingBalance()).isEqualByComparingTo("8.00");
        assertThat(receipt.paymentStatus()).isEqualTo("PARTIALLY PAID");
        assertThat(receiptService.getReceipt(unpaid.id()).paymentStatus()).isEqualTo("UNPAID");
        assertThat(receiptService.getReceipt(paid.id()).paymentStatus()).isEqualTo("PAID");
        assertThat(paymentService.getPayment(invalidCrossCustomerPayment.getId()).allocations()).isEmpty();

        assertThat(paymentAllocationRepository.sumValidAllocatedPaymentsForCustomer(
                customer.getId(), SaleStatus.COMPLETED)).isEqualByComparingTo("14.00");
        assertThat(paymentAllocationRepository.findBySaleIdIn(
                List.of(partiallyPaid.id(), unpaid.id(), paid.id(), otherUnpaid.id()))).hasSize(4);

        mockMvc.perform(get("/sales/" + partiallyPaid.id()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Total paid to date")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("PARTIALLY PAID")));
        mockMvc.perform(get("/customers/" + customer.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Payment status")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("PARTIALLY PAID")));
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Payment status")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("PARTIALLY PAID")));
    }

    @Test
    void roundsEachLineToTwoDecimalPlacesUsingHalfUp() {
        SaleResponse sale = saleService.createSale(request(
                UUID.randomUUID().toString(), "0.00", null, line(productB, "0.333"))).sale();

        assertThat(sale.items().getFirst().lineTotal()).isEqualByComparingTo("0.67");
        assertThat(sale.totalAmount()).isEqualByComparingTo("0.67");
    }

    @Test
    void rejectsInsufficientStockWithoutChangingInventory() {
        String requestKey = UUID.randomUUID().toString();
        long movementsBefore = saleMovementCount(productA.getId());

        assertThrows(InsufficientStockException.class, () -> saleService.createSale(request(
                requestKey, "0.00", null, line(productA, "11"))));

        assertThat(productQuantity(productA)).isEqualByComparingTo("10.000");
        assertThat(saleRepository.findByRequestKey(requestKey)).isEmpty();
        assertThat(saleMovementCount(productA.getId())).isEqualTo(movementsBefore);
    }

    @Test
    void rejectsInactiveCustomer() {
        customer.deactivate();
        customerRepository.save(customer);
        long salesBefore = saleRepository.count();

        assertThrows(SaleValidationException.class, () -> saleService.createSale(request(
                UUID.randomUUID().toString(), "0.00", null, line(productA, "1"))));
        assertThat(saleRepository.count()).isEqualTo(salesBefore);
    }

    @Test
    void rejectsInactiveProduct() {
        productA.deactivate();
        productRepository.save(productA);
        long salesBefore = saleRepository.count();

        assertThrows(SaleValidationException.class, () -> saleService.createSale(request(
                UUID.randomUUID().toString(), "0.00", null, line(productA, "1"))));
        assertThat(saleRepository.count()).isEqualTo(salesBefore);
    }

    @Test
    void rejectsPaymentGreaterThanInvoiceTotal() {
        String requestKey = UUID.randomUUID().toString();

        assertThrows(SaleValidationException.class, () -> saleService.createSale(request(
                requestKey, "20.01", PaymentMethod.CASH, line(productA, "2"))));
        assertThat(saleRepository.findByRequestKey(requestKey)).isEmpty();
        assertThat(productQuantity(productA)).isEqualByComparingTo("10.000");
    }

    @Test
    void rejectsNegativeInitialPayment() {
        assertThrows(SaleValidationException.class, () -> saleService.createSale(request(
                UUID.randomUUID().toString(), "-0.01", null, line(productA, "1"))));
        assertThat(productQuantity(productA)).isEqualByComparingTo("10.000");
    }

    @Test
    void rollsBackEarlierItemStockAndMovementWhenLaterItemIsInsufficient() {
        Product shortStockProduct = saveProduct("Short stock " + UUID.randomUUID(), "3.00", "1");
        String requestKey = UUID.randomUUID().toString();
        long salesBefore = saleRepository.count();
        long productAMovementsBefore = saleMovementCount(productA.getId());

        assertThrows(InsufficientStockException.class, () -> saleService.createSale(request(
                requestKey,
                "0.00",
                null,
                line(productA, "2"),
                line(shortStockProduct, "2"))));

        assertThat(productQuantity(productA)).isEqualByComparingTo("10.000");
        assertThat(productQuantity(shortStockProduct)).isEqualByComparingTo("1.000");
        assertThat(saleRepository.count()).isEqualTo(salesBefore);
        assertThat(saleRepository.findByRequestKey(requestKey)).isEmpty();
        assertThat(saleMovementCount(productA.getId())).isEqualTo(productAMovementsBefore);
        assertThat(saleMovementCount(shortStockProduct.getId())).isZero();
    }

    @Test
    void treatsRepeatedSubmissionKeyAsOneSale() {
        String requestKey = UUID.randomUUID().toString();
        SaleRequest saleRequest = request(requestKey, "0.00", null, line(productA, "2"));
        long salesBefore = saleRepository.count();
        long movementsBefore = saleMovementCount(productA.getId());

        SaleCreationResult first = saleService.createSale(saleRequest);
        SaleCreationResult repeated = saleService.createSale(saleRequest);

        assertThat(first.created()).isTrue();
        assertThat(repeated.created()).isFalse();
        assertThat(repeated.sale().id()).isEqualTo(first.sale().id());
        assertThat(saleRepository.count()).isEqualTo(salesBefore + 1);
        assertThat(productQuantity(productA)).isEqualByComparingTo("8.000");
        assertThat(saleMovementCount(productA.getId())).isEqualTo(movementsBefore + 1);
    }

    @Test
    void concurrentSalesCannotOversellTheSameProduct() throws Exception {
        Customer otherCustomer = customerRepository.save(new Customer(
                "Farmer " + UUID.randomUUID(), "9123456789", "Rampur", null, null));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> first = executor.submit(() -> trySale(customer, ready, start));
            Future<Boolean> second = executor.submit(() -> trySale(otherCustomer, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Boolean> results = new ArrayList<>(List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)));

            assertThat(results).containsExactlyInAnyOrder(true, false);
            assertThat(productQuantity(productA)).isEqualByComparingTo("4.000");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentSaleAndProductUpdatePreserveStockAndMovementHistory() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 5; round++) {
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);
                String updatedName = "Updated Spray " + round;
                ProductForm form = productDetailsForm(updatedName);

                Future<SaleCreationResult> sale = executor.submit(() -> {
                    awaitConcurrentStart(ready, start);
                    return saleService.createSale(request(
                            UUID.randomUUID().toString(), "0.00", null, line(productA, "1")));
                });
                Future<Product> update = executor.submit(() -> {
                    awaitConcurrentStart(ready, start);
                    return productService.updateProduct(productA.getId(), form);
                });

                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                assertThat(sale.get(10, TimeUnit.SECONDS).created()).isTrue();
                assertThat(update.get(10, TimeUnit.SECONDS).getName()).isEqualTo(updatedName);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(productQuantity(productA)).isEqualByComparingTo("5.000");
        assertThat(productQuantity(productA).signum()).isGreaterThanOrEqualTo(0);
        assertThat(saleMovements(productA.getId())).hasSize(5);
        assertThat(saleMovements(productA.getId()).stream()
                .map(StockMovement::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("5.000");
    }

    @Test
    void preservesProductNameAndPriceSnapshotsAfterProductChanges() {
        SaleResponse created = saleService.createSale(request(
                UUID.randomUUID().toString(), "0.00", null, line(productA, "1"))).sale();

        productA.updateDetails("Renamed product", null, "bottle", new BigDecimal("99.00"), BigDecimal.ZERO);
        productRepository.save(productA);
        SaleResponse retrieved = saleService.getSale(created.id());

        assertThat(retrieved.items().getFirst().productName()).startsWith("Spray A ");
        assertThat(retrieved.items().getFirst().unitPrice()).isEqualByComparingTo("10.00");
    }

    @Test
    @WithMockUser
    void rendersSalesPagesAndCreatesSaleThroughTheApi() throws Exception {
        mockMvc.perform(get("/sales"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sales")));
        mockMvc.perform(get("/sales/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Remaining balance")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Search products")));

        MvcResult pageSale = mockMvc.perform(post("/sales")
                        .with(csrf())
                        .param("customerId", customer.getId().toString())
                        .param("requestKey", UUID.randomUUID().toString())
                        .param("amountPaid", "1.00")
                        .param("paymentMethod", "CASH")
                        .param("items[0].productId", productA.getId().toString())
                        .param("items[0].quantity", "1"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        mockMvc.perform(get(pageSale.getResponse().getRedirectedUrl()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Total paid to date")));

        String requestKey = UUID.randomUUID().toString();
        String body = """
                {
                  "customerId": %d,
                  "requestKey": "%s",
                  "amountPaid": 2.00,
                  "paymentMethod": "CASH",
                  "items": [{"productId": %d, "quantity": 1.5}]
                }
                """.formatted(customer.getId(), requestKey, productA.getId());
        MvcResult created = mockMvc.perform(post("/api/sales")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoiceNumber").value(org.hamcrest.Matchers.startsWith("INV-")))
                .andExpect(jsonPath("$.totalAmount").value(15.00))
                .andExpect(jsonPath("$.amountPaid").value(2.00))
                .andExpect(jsonPath("$.outstandingBalance").value(13.00))
                .andReturn();

        String customerSaleId = URI.create(created.getResponse().getHeader("Location")).getPath()
                .substring("/api/sales/".length());
        mockMvc.perform(get("/api/sales/" + customerSaleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productName").value(productA.getName()));
        mockMvc.perform(get("/api/sales"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].customerName", org.hamcrest.Matchers.hasItem(customer.getName())));
    }

    @Test
    @WithMockUser
    void rendersSalesFormAndReportsInsufficientStock() throws Exception {
        mockMvc.perform(post("/sales")
                        .with(csrf())
                        .param("customerId", customer.getId().toString())
                        .param("requestKey", UUID.randomUUID().toString())
                        .param("amountPaid", "0.00")
                        .param("items[0].productId", productA.getId().toString())
                        .param("items[0].quantity", "50"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("only 10.000 is available")));
    }

    private SaleRequest request(
            String requestKey,
            String amountPaid,
            PaymentMethod paymentMethod,
            SaleItemRequest... items) {
        SaleRequest request = new SaleRequest();
        request.setCustomerId(customer.getId());
        request.setRequestKey(requestKey);
        request.setAmountPaid(new BigDecimal(amountPaid));
        request.setPaymentMethod(paymentMethod);
        request.setItems(List.of(items));
        return request;
    }

    private SaleItemRequest line(Product product, String quantity) {
        SaleItemRequest item = new SaleItemRequest();
        item.setProductId(product.getId());
        item.setQuantity(new BigDecimal(quantity));
        return item;
    }

    private PaymentForm paymentForm(Customer paymentCustomer, Long saleId, String amount) {
        PaymentForm form = new PaymentForm();
        form.setCustomerId(paymentCustomer.getId());
        form.setAmount(new BigDecimal(amount));
        form.setPaymentDate(LocalDateTime.now().withSecond(0).withNano(0));
        form.setPaymentMethod(PaymentMethod.UPI);
        form.setTransactionReference("FIN-" + UUID.randomUUID());
        form.setRequestKey(UUID.randomUUID().toString());
        PaymentAllocationForm allocation = new PaymentAllocationForm();
        allocation.setSaleId(saleId);
        allocation.setAmount(new BigDecimal(amount));
        form.setAllocations(List.of(allocation));
        return form;
    }

    private CustomerPurchaseHistoryItem historyFor(CustomerProfileResponse profile, Long saleId) {
        return profile.purchaseHistory().stream()
                .filter(item -> item.saleId().equals(saleId))
                .findFirst()
                .orElseThrow();
    }

    private RecentSaleSummary recentSaleFor(DashboardView dashboard, Long saleId) {
        return dashboard.recentSales().stream()
                .filter(sale -> sale.saleId().equals(saleId))
                .findFirst()
                .orElseThrow();
    }

    private boolean trySale(Customer saleCustomer, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        start.await();
        SaleRequest saleRequest = request(UUID.randomUUID().toString(), "0.00", null, line(productA, "6"));
        saleRequest.setCustomerId(saleCustomer.getId());
        try {
            return saleService.createSale(saleRequest).created();
        } catch (InsufficientStockException exception) {
            return false;
        }
    }

    private void awaitConcurrentStart(CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent test start timed out");
        }
    }

    private ProductForm productDetailsForm(String name) {
        ProductForm form = new ProductForm();
        form.setName(name);
        form.setBrand(productA.getBrand());
        form.setUnit(productA.getUnit());
        form.setQuantity(productA.getQuantity());
        form.setSellingPrice(productA.getSellingPrice());
        form.setLowStockThreshold(productA.getLowStockThreshold());
        return form;
    }

    private Product saveProduct(String name, String price, String quantity) {
        return productRepository.save(new Product(
                name,
                null,
                "bottle",
                new BigDecimal(quantity),
                new BigDecimal(price),
                BigDecimal.ONE));
    }

    private BigDecimal productQuantity(Product product) {
        return productRepository.findById(product.getId()).orElseThrow().getQuantity();
    }

    private List<StockMovement> saleMovements(Long productId) {
        return stockMovementRepository.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .filter(movement -> movement.getMovementType() == MovementType.SALE)
                .toList();
    }

    private long saleMovementCount(Long productId) {
        return saleMovements(productId).size();
    }
}
