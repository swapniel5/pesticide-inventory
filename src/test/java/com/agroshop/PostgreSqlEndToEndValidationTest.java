package com.agroshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import com.agroshop.customer.CustomerForm;
import com.agroshop.customer.CustomerProfileResponse;
import com.agroshop.customer.CustomerService;
import com.agroshop.dashboard.DashboardService;
import com.agroshop.dashboard.DashboardView;
import com.agroshop.inventory.InsufficientStockException;
import com.agroshop.inventory.MovementType;
import com.agroshop.inventory.StockMovement;
import com.agroshop.inventory.StockMovementRepository;
import com.agroshop.payment.PaymentAllocationForm;
import com.agroshop.payment.PaymentForm;
import com.agroshop.payment.PaymentMethod;
import com.agroshop.payment.PaymentRepository;
import com.agroshop.payment.PaymentService;
import com.agroshop.payment.PaymentValidationException;
import com.agroshop.product.Product;
import com.agroshop.product.ProductForm;
import com.agroshop.product.ProductService;
import com.agroshop.receipt.ReceiptService;
import com.agroshop.receipt.ReceiptView;
import com.agroshop.sales.SaleCreationResult;
import com.agroshop.sales.SaleItemRequest;
import com.agroshop.sales.SaleRepository;
import com.agroshop.sales.SaleRequest;
import com.agroshop.sales.SaleResponse;
import com.agroshop.sales.SaleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=${AGROSHOP_PG_QA_URL}",
        "spring.datasource.username=${AGROSHOP_PG_QA_USERNAME}",
        "spring.datasource.password=${AGROSHOP_PG_QA_PASSWORD}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.security.user.name=${SPRING_SECURITY_USER_NAME}",
        "spring.security.user.password=${SPRING_SECURITY_USER_PASSWORD}"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "AGROSHOP_PG_QA_URL", matches = "jdbc:postgresql://.+")
class PostgreSqlEndToEndValidationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private SaleService saleService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ReceiptService receiptService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void validatesFinancialInventoryAndSecurityWorkflowsOnPostgreSql() throws Exception {
        String ownerName = System.getenv("SPRING_SECURITY_USER_NAME");
        String ownerPassword = System.getenv("SPRING_SECURITY_USER_PASSWORD");
        mockMvc.perform(get("/products"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/").with(httpBasic(ownerName, ownerPassword)))
                .andExpect(status().isOk());

        Product product = productService.createProduct(productForm("PG QA Product", "20.000", "10.00"));
        productService.recordStockMovement(
                product.getId(), MovementType.PURCHASE, new BigDecimal("2.000"), "PostgreSQL QA restock");
        productService.recordStockMovement(
                product.getId(), MovementType.ADJUSTMENT_OUT, new BigDecimal("1.000"), "PostgreSQL QA adjustment");
        assertThat(productService.getProduct(product.getId()).getQuantity()).isEqualByComparingTo("21.000");

        Customer customer = customerService.createCustomer(customerForm());
        SaleResponse initialSale = createSale(customer, product, "4.000", "10.00");
        PaymentForm partialPayment = paymentForm(customer, "10.00", allocation(initialSale.id(), "10.00"));
        paymentService.recordPayment(partialPayment);

        SaleResponse secondSale = createSale(customer, product, "3.000", "0.00");
        SaleResponse thirdSale = createSale(customer, product, "2.000", "0.00");
        var multiInvoicePayment = paymentService.recordPayment(paymentForm(
                customer,
                "25.00",
                allocation(secondSale.id(), "15.00"),
                allocation(thirdSale.id(), "10.00")));
        assertThat(multiInvoicePayment.allocations()).hasSize(2);
        assertThat(multiInvoicePayment.allocations()).extracting("allocatedAmount")
                .containsExactly(new BigDecimal("15.00"), new BigDecimal("10.00"));

        SaleResponse invoiceDetails = saleService.getSale(initialSale.id());
        assertThat(invoiceDetails.totalAmount()).isEqualByComparingTo("40.00");
        assertThat(invoiceDetails.amountPaid()).isEqualByComparingTo("20.00");
        assertThat(invoiceDetails.outstandingBalance()).isEqualByComparingTo("20.00");

        CustomerProfileResponse customerProfile = customerService.getCustomerProfile(customer.getId());
        assertThat(customerProfile.financialSummary().totalPurchases()).isEqualByComparingTo("90.00");
        assertThat(customerProfile.financialSummary().totalPaymentsReceived()).isEqualByComparingTo("45.00");
        assertThat(customerProfile.financialSummary().outstandingBalance()).isEqualByComparingTo("45.00");
        assertThat(customerProfile.purchaseHistory().stream()
                .map(item -> item.outstandingAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("45.00");

        DashboardView dashboard = dashboardService.getDashboard();
        assertThat(dashboard.totalSales()).isEqualByComparingTo("90.00");
        assertThat(dashboard.paymentsReceived()).isEqualByComparingTo("45.00");
        assertThat(dashboard.outstandingCredit()).isEqualByComparingTo("45.00");
        assertThat(dashboard.outstandingCredit())
                .isEqualByComparingTo(customerProfile.financialSummary().outstandingBalance());

        ReceiptView receipt = receiptService.getReceipt(initialSale.id());
        assertThat(receipt.totalPaid()).isEqualByComparingTo("20.00");
        assertThat(receipt.outstandingBalance()).isEqualByComparingTo("20.00");
        assertThat(receipt.paymentStatus()).isEqualTo("PARTIALLY PAID");

        BigDecimal validPayments = paymentService.findCustomerPayments(customer.getId()).stream()
                .flatMap(payment -> payment.allocations().stream())
                .map(allocation -> allocation.allocatedAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(validPayments).isEqualByComparingTo("45.00");

        long paymentCountBeforeOverpayment = paymentRepository.count();
        assertThatThrownBy(() -> paymentService.recordPayment(
                paymentForm(customer, "46.00")))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("total outstanding balance");
        assertThat(paymentRepository.count()).isEqualTo(paymentCountBeforeOverpayment);

        BigDecimal stockBeforeFailedSale = productService.getProduct(product.getId()).getQuantity();
        long salesBeforeFailedSale = saleRepository.count();
        int movementsBeforeFailedSale = stockMovementRepository
                .findByProductIdOrderByCreatedAtDesc(product.getId()).size();
        assertThatThrownBy(() -> saleService.createSale(saleRequest(
                customer, product, "100.000", "0.00")))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(productService.getProduct(product.getId()).getQuantity())
                .isEqualByComparingTo(stockBeforeFailedSale);
        assertThat(saleRepository.count()).isEqualTo(salesBeforeFailedSale);
        assertThat(stockMovementRepository.findByProductIdOrderByCreatedAtDesc(product.getId()))
                .hasSize(movementsBeforeFailedSale);

        List<StockMovement> movements =
                stockMovementRepository.findByProductIdOrderByCreatedAtDesc(product.getId());
        BigDecimal opened = movementTotal(movements, MovementType.OPENING);
        BigDecimal purchased = movementTotal(movements, MovementType.PURCHASE);
        BigDecimal adjustedOut = movementTotal(movements, MovementType.ADJUSTMENT_OUT);
        BigDecimal sold = movementTotal(movements, MovementType.SALE);
        assertThat(opened.add(purchased).subtract(adjustedOut).subtract(sold))
                .isEqualByComparingTo("12.000");
        assertThat(productService.getProduct(product.getId()).getQuantity()).isEqualByComparingTo("12.000");

        Product concurrentProduct =
                productService.createProduct(productForm("PG QA Concurrent Product", "5.000", "1.00"));
        runConcurrentSaleAndProductUpdate(customer, concurrentProduct);
        assertThat(productService.getProduct(concurrentProduct.getId()).getQuantity()).isEqualByComparingTo("4.000");
        assertThat(movementTotal(
                stockMovementRepository.findByProductIdOrderByCreatedAtDesc(concurrentProduct.getId()),
                MovementType.SALE)).isEqualByComparingTo("1.000");
    }

    private void runConcurrentSaleAndProductUpdate(Customer customer, Product product) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        ProductForm changes = productForm("PG QA Concurrent Product Updated", "5.000", "1.00");

        try {
            Future<SaleCreationResult> sale = executor.submit(() -> {
                awaitStart(ready, start);
                return saleService.createSale(saleRequest(customer, product, "1.000", "0.00"));
            });
            Future<Product> update = executor.submit(() -> {
                awaitStart(ready, start);
                return productService.updateProduct(product.getId(), changes);
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(sale.get(30, TimeUnit.SECONDS).created()).isTrue();
            assertThat(update.get(30, TimeUnit.SECONDS).getName()).isEqualTo("PG QA Concurrent Product Updated");
        } finally {
            executor.shutdownNow();
        }
    }

    private void awaitStart(CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("PostgreSQL concurrency test start timed out");
        }
    }

    private ProductForm productForm(String name, String quantity, String price) {
        ProductForm form = new ProductForm();
        form.setName(name);
        form.setBrand("QA");
        form.setUnit("bottle");
        form.setQuantity(new BigDecimal(quantity));
        form.setSellingPrice(new BigDecimal(price));
        form.setLowStockThreshold(new BigDecimal("3.000"));
        return form;
    }

    private CustomerForm customerForm() {
        CustomerForm form = new CustomerForm();
        form.setName("PG QA Customer");
        form.setPhone("9123456789");
        form.setVillage("QA Village");
        return form;
    }

    private SaleResponse createSale(Customer customer, Product product, String quantity, String amountPaid) {
        return saleService.createSale(saleRequest(customer, product, quantity, amountPaid)).sale();
    }

    private SaleRequest saleRequest(Customer customer, Product product, String quantity, String amountPaid) {
        SaleRequest request = new SaleRequest();
        request.setCustomerId(customer.getId());
        request.setRequestKey("pg-qa-sale-" + UUID.randomUUID());
        request.setAmountPaid(new BigDecimal(amountPaid));
        if (new BigDecimal(amountPaid).signum() > 0) {
            request.setPaymentMethod(PaymentMethod.CASH);
        }
        SaleItemRequest item = new SaleItemRequest();
        item.setProductId(product.getId());
        item.setQuantity(new BigDecimal(quantity));
        request.setItems(List.of(item));
        return request;
    }

    private PaymentForm paymentForm(
            Customer customer,
            String amount,
            PaymentAllocationForm... allocations) {
        PaymentForm form = new PaymentForm();
        form.setCustomerId(customer.getId());
        form.setAmount(new BigDecimal(amount));
        form.setPaymentDate(LocalDateTime.now().withSecond(0).withNano(0));
        form.setPaymentMethod(PaymentMethod.UPI);
        form.setTransactionReference("PG-QA-" + UUID.randomUUID());
        form.setNotes("Isolated PostgreSQL QA");
        form.setRequestKey("pg-qa-payment-" + UUID.randomUUID());
        form.setAllocations(new ArrayList<>(List.of(allocations)));
        return form;
    }

    private PaymentAllocationForm allocation(Long saleId, String amount) {
        PaymentAllocationForm allocation = new PaymentAllocationForm();
        allocation.setSaleId(saleId);
        allocation.setAmount(new BigDecimal(amount));
        return allocation;
    }

    private BigDecimal movementTotal(List<StockMovement> movements, MovementType type) {
        return movements.stream()
                .filter(movement -> movement.getMovementType() == type)
                .map(StockMovement::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
