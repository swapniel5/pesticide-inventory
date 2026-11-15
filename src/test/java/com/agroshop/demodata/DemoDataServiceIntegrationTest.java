package com.agroshop.demodata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.agroshop.customer.Customer;
import com.agroshop.customer.CustomerRepository;
import com.agroshop.customer.CustomerService;
import com.agroshop.payment.Payment;
import com.agroshop.payment.PaymentAllocation;
import com.agroshop.payment.PaymentAllocationRepository;
import com.agroshop.payment.PaymentRepository;
import com.agroshop.product.Product;
import com.agroshop.product.ProductRepository;
import com.agroshop.sales.Sale;
import com.agroshop.sales.SaleItem;
import com.agroshop.sales.SaleItemRepository;
import com.agroshop.sales.SaleRepository;
import com.agroshop.sales.SaleRequest;
import com.agroshop.sales.SaleService;
import com.agroshop.sales.SaleStatus;
import com.agroshop.dashboard.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agroshop_demo",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "agroshop.demo-data.run-on-startup=false"
})
@ActiveProfiles("demo")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Transactional
class DemoDataServiceIntegrationTest {

    @Autowired
    private DemoDataService demoDataService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private SaleItemRepository saleItemRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentAllocationRepository paymentAllocationRepository;

    @Autowired
    private SaleService saleService;

    @Autowired
    private DashboardService dashboardService;

    @Test
    void createsConsistentDemoDataAndRerunningDoesNotDuplicateRecords() {
        DemoDataSummary first = demoDataService.seed();
        DemoDataSummary second = demoDataService.seed();

        assertThat(first.outcome()).isEqualTo("generated");
        assertThat(first.productsCreated()).isEqualTo(20);
        assertThat(first.customersCreated()).isEqualTo(20);
        assertThat(first.salesCreated()).isEqualTo(30);
        assertThat(first.saleItemsCreated()).isEqualTo(73);
        assertThat(first.paymentsCreated()).isBetween(35, 45);
        assertThat(first.lowStockProducts()).isBetween(3, 4);
        assertThat(second.outcome()).isEqualTo("already exists");
        assertThat(second.productsCreated()).isEqualTo(first.productsCreated());
        assertThat(second.customersCreated()).isEqualTo(first.customersCreated());
        assertThat(second.salesCreated()).isEqualTo(first.salesCreated());
        assertThat(second.paymentsCreated()).isEqualTo(first.paymentsCreated());

        assertThat(productRepository.findByBrand(DemoDataService.PRODUCT_BRAND)).hasSize(20);
        assertThat(customerRepository.findByNotesStartingWith(DemoDataService.CUSTOMER_NOTE_PREFIX)).hasSize(20);
        assertThat(saleRepository.findByRequestKeyStartingWith(DemoDataService.SALE_REQUEST_PREFIX)).hasSize(30);
        assertThat(paymentRepository.findByRequestKeyStartingWith(DemoDataService.PAYMENT_REQUEST_PREFIX))
                .hasSize(17);
    }

    @Test
    void allStockSalesPaymentsAndCustomerBalancesReconcileWithDashboard() {
        demoDataService.seed();
        var demoProducts = productRepository.findByBrand(DemoDataService.PRODUCT_BRAND);
        var demoCustomers = customerRepository.findByNotesStartingWith(DemoDataService.CUSTOMER_NOTE_PREFIX);
        var demoSales = saleRepository.findByRequestKeyStartingWith(DemoDataService.SALE_REQUEST_PREFIX);
        Set<Long> demoProductIds = new HashSet<>(demoProducts.stream().map(Product::getId).toList());
        Set<Long> demoCustomerIds = new HashSet<>(demoCustomers.stream().map(Customer::getId).toList());
        Set<Long> demoSaleIds = new HashSet<>(demoSales.stream().map(Sale::getId).toList());

        assertThat(demoProducts).allSatisfy(product -> assertThat(product.getQuantity()).isNotNegative());
        assertThat(demoSales).hasSize(30).allSatisfy(sale -> {
            assertThat(demoCustomerIds).contains(sale.getCustomer().getId());
            assertThat(sale.getStatus()).isEqualTo(SaleStatus.COMPLETED);
            assertThat(sale.getInvoiceNumber()).matches("INV-\\d{4}-\\d{4,}");
            var items = saleItemRepository.findBySaleIdOrderByIdAsc(sale.getId());
            assertThat(items.size()).isBetween(1, 4);
            assertThat(items).allSatisfy(item -> {
                assertThat(demoProductIds).contains(item.getProduct().getId());
                assertThat(item.getLineTotal()).isEqualByComparingTo(
                        item.getQuantity().multiply(item.getUnitPrice()).setScale(2));
            });
            assertThat(items.stream().map(SaleItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add))
                    .isEqualByComparingTo(sale.getTotalAmount());
        });

        var allocations = paymentAllocationRepository.findBySaleIdIn(demoSaleIds.stream().toList());
        Map<Long, BigDecimal> paidBySale = new HashMap<>();
        Map<Long, BigDecimal> allocatedByPayment = new HashMap<>();
        for (PaymentAllocation allocation : allocations) {
            assertThat(allocation.getPayment().getCustomer().getId())
                    .isEqualTo(allocation.getSale().getCustomer().getId());
            paidBySale.merge(
                    allocation.getSale().getId(), allocation.getAllocatedAmount(), BigDecimal::add);
            allocatedByPayment.merge(
                    allocation.getPayment().getId(), allocation.getAllocatedAmount(), BigDecimal::add);
        }
        for (Sale sale : demoSales) {
            assertThat(paidBySale.getOrDefault(sale.getId(), BigDecimal.ZERO))
                    .isLessThanOrEqualTo(sale.getTotalAmount());
        }
        for (Payment payment : paymentRepository.findAll()) {
            assertThat(demoCustomerIds).contains(payment.getCustomer().getId());
            assertThat(allocatedByPayment.getOrDefault(payment.getId(), BigDecimal.ZERO))
                    .isEqualByComparingTo(payment.getAmount());
        }

        BigDecimal customerOutstanding = demoCustomers.stream()
                .map(Customer::getId)
                .map(customerService::getCustomerProfile)
                .map(profile -> profile.financialSummary().outstandingBalance())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var dashboard = dashboardService.getDashboard();
        DemoDataSummary summary = demoDataService.seed();

        assertThat(summary.totalSales()).isEqualByComparingTo(dashboard.totalSales());
        assertThat(summary.totalPayments()).isEqualByComparingTo(dashboard.paymentsReceived());
        assertThat(summary.outstandingCredit()).isEqualByComparingTo(dashboard.outstandingCredit());
        assertThat(customerOutstanding).isEqualByComparingTo(dashboard.outstandingCredit());
        assertThat(dashboard.lowStockProductCount()).isEqualTo(4);
        assertThat(demoDataService.seed().customersWithOutstanding()).isNotEmpty();
    }

    @Test
    void resetDeletesRegisteredDemoRowsButPreservesUnrelatedBusinessRecords() {
        demoDataService.seed();
        Product businessProduct = productRepository.save(new Product(
                "Existing business product",
                "Local supplier",
                "bottle",
                new BigDecimal("10.000"),
                new BigDecimal("100.00"),
                new BigDecimal("2.000")));
        Customer businessCustomer = customerRepository.save(new Customer(
                "Existing business customer",
                "9876543210",
                "Nashik",
                null,
                "Not demo data"));

        DemoDataSummary reset = demoDataService.reset();

        assertThat(reset.productsCreated()).isZero();
        assertThat(reset.customersCreated()).isZero();
        assertThat(reset.salesCreated()).isZero();
        assertThat(reset.paymentsCreated()).isZero();
        assertThat(productRepository.findById(businessProduct.getId())).isPresent();
        assertThat(customerRepository.findById(businessCustomer.getId())).isPresent();
        assertThat(productRepository.findByBrand(DemoDataService.PRODUCT_BRAND)).isEmpty();
        assertThat(customerRepository.findByNotesStartingWith(DemoDataService.CUSTOMER_NOTE_PREFIX)).isEmpty();
    }

    @Test
    void resetStopsWithoutDeletingDemoRowsWhenUnregisteredSalesUseDemoData() {
        demoDataService.seed();
        Customer demoCustomer =
                customerRepository.findByNotesStartingWith(DemoDataService.CUSTOMER_NOTE_PREFIX).getFirst();
        Product demoProduct = productRepository.findByBrand(DemoDataService.PRODUCT_BRAND).getFirst();
        SaleRequest nonDemoSale = new SaleRequest();
        nonDemoSale.setCustomerId(demoCustomer.getId());
        nonDemoSale.setRequestKey("business-sale-" + UUID.randomUUID());
        var item = new com.agroshop.sales.SaleItemRequest();
        item.setProductId(demoProduct.getId());
        item.setQuantity(BigDecimal.ONE);
        nonDemoSale.setItems(java.util.List.of(item));
        nonDemoSale.setAmountPaid(BigDecimal.ZERO);
        saleService.createSale(nonDemoSale);

        assertThatThrownBy(demoDataService::reset)
                .isInstanceOf(DemoDataSafetyException.class)
                .hasMessageContaining("No records were deleted");
        assertThat(productRepository.findByBrand(DemoDataService.PRODUCT_BRAND)).hasSize(20);
        assertThat(saleRepository.findByRequestKeyStartingWith(DemoDataService.SALE_REQUEST_PREFIX)).hasSize(30);
    }
}
