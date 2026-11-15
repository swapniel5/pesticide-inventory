package com.agroshop.demodata;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.agroshop.customer.Customer;
import com.agroshop.customer.CustomerForm;
import com.agroshop.customer.CustomerRepository;
import com.agroshop.customer.CustomerService;
import com.agroshop.inventory.StockMovementRepository;
import com.agroshop.payment.OutstandingInvoice;
import com.agroshop.payment.PaymentAllocationForm;
import com.agroshop.payment.PaymentAllocationRepository;
import com.agroshop.payment.PaymentForm;
import com.agroshop.payment.PaymentMethod;
import com.agroshop.payment.PaymentRepository;
import com.agroshop.payment.PaymentService;
import com.agroshop.payment.PaymentView;
import com.agroshop.product.Product;
import com.agroshop.product.ProductForm;
import com.agroshop.product.ProductRepository;
import com.agroshop.product.ProductService;
import com.agroshop.sales.SaleCreationResult;
import com.agroshop.sales.SaleItemRequest;
import com.agroshop.sales.SaleRepository;
import com.agroshop.sales.SaleRequest;
import com.agroshop.sales.SaleResponse;
import com.agroshop.sales.SaleItemRepository;
import com.agroshop.sales.SaleService;
import com.agroshop.sales.SaleStatus;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoDataService {

    static final String PRODUCT_BRAND = "AgroShop Demo Catalogue";
    static final String CUSTOMER_NOTE_PREFIX = "[AGROSHOP DEMO DATA]";
    static final String SALE_REQUEST_PREFIX = "AGROSHOP-DEMO-V1-SALE-";
    static final String PAYMENT_REQUEST_PREFIX = "AGROSHOP-DEMO-V1-PAYMENT-";

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final int EXPECTED_PRODUCTS = 20;
    private static final int EXPECTED_CUSTOMERS = 20;
    private static final int EXPECTED_SALES = 30;
    private static final int EXPECTED_PAYMENTS = 40;
    private static final List<Integer> CUSTOMERS_WITH_SUBSEQUENT_PAYMENTS = List.of(1, 2, 3, 5, 6, 7, 9);
    private static final Set<Integer> CUSTOMERS_TO_SETTLE = Set.of(2, 6, 9);

    private static final List<ProductSeed> PRODUCT_SEEDS = List.of(
            new ProductSeed("Demo Crop Care Product 01", "bottle", "135.00", "22.000", "18.500"),
            new ProductSeed("Demo Crop Care Product 02", "packet", "210.00", "22.000", "18.500"),
            new ProductSeed("Demo Crop Care Product 03", "litre", "385.00", "22.000", "18.500"),
            new ProductSeed("Demo Crop Care Product 04", "kg", "165.00", "22.000", "18.500"),
            new ProductSeed("Demo Orchard Care Product 05", "bottle", "275.00", "250.000", "20.000"),
            new ProductSeed("Demo Orchard Care Product 06", "packet", "95.00", "250.000", "20.000"),
            new ProductSeed("Demo Orchard Care Product 07", "litre", "520.00", "250.000", "20.000"),
            new ProductSeed("Demo Orchard Care Product 08", "kg", "180.00", "250.000", "20.000"),
            new ProductSeed("Demo Field Care Product 09", "bottle", "325.00", "250.000", "20.000"),
            new ProductSeed("Demo Field Care Product 10", "packet", "120.00", "250.000", "20.000"),
            new ProductSeed("Demo Field Care Product 11", "litre", "640.00", "250.000", "20.000"),
            new ProductSeed("Demo Field Care Product 12", "kg", "235.00", "250.000", "20.000"),
            new ProductSeed("Demo Plant Care Product 13", "bottle", "155.00", "250.000", "20.000"),
            new ProductSeed("Demo Plant Care Product 14", "packet", "290.00", "250.000", "20.000"),
            new ProductSeed("Demo Plant Care Product 15", "litre", "455.00", "250.000", "20.000"),
            new ProductSeed("Demo Plant Care Product 16", "kg", "195.00", "250.000", "20.000"),
            new ProductSeed("Demo Farm Care Product 17", "bottle", "345.00", "250.000", "20.000"),
            new ProductSeed("Demo Farm Care Product 18", "packet", "110.00", "250.000", "20.000"),
            new ProductSeed("Demo Farm Care Product 19", "litre", "575.00", "250.000", "20.000"),
            new ProductSeed("Demo Farm Care Product 20", "kg", "225.00", "250.000", "20.000"));

    private static final List<CustomerSeed> CUSTOMER_SEEDS = List.of(
            new CustomerSeed("Aarav Patil", "Niphad"),
            new CustomerSeed("Meera Shinde", "Dindori"),
            new CustomerSeed("Vihaan Pawar", "Sinnar"),
            new CustomerSeed("Anaya Jadhav", "Yeola"),
            new CustomerSeed("Ishaan Deshmukh", "Igatpuri"),
            new CustomerSeed("Saanvi More", "Chandwad"),
            new CustomerSeed("Aditya Borse", "Kalwan"),
            new CustomerSeed("Myra Chavan", "Deola"),
            new CustomerSeed("Reyansh Nikam", "Trimbakeshwar"),
            new CustomerSeed("Aadhya Gaikwad", "Baglan"),
            new CustomerSeed("Rohan Ahire", "Surgana"),
            new CustomerSeed("Gauri Mahale", "Peth"),
            new CustomerSeed("Kabir Sonawane", "Lasalgaon"),
            new CustomerSeed("Ira Wagh", "Ozar"),
            new CustomerSeed("Arjun Kharat", "Pimpalgaon Baswant"),
            new CustomerSeed("Diya Bhamre", "Nandgaon"),
            new CustomerSeed("Vedant Boraste", "Satana"),
            new CustomerSeed("Riya Nikam", "Ghoti"),
            new CustomerSeed("Dhruv Suryawanshi", "Vinchur"),
            new CustomerSeed("Tara Pawar", "Niphad"));

    private final DemoDataDatabaseGuard databaseGuard;
    private final DemoDataRegistry registry;
    private final ProductService productService;
    private final ProductRepository productRepository;
    private final CustomerService customerService;
    private final CustomerRepository customerRepository;
    private final SaleService saleService;
    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final StockMovementRepository stockMovementRepository;
    private final EntityManager entityManager;

    public DemoDataService(
            DemoDataDatabaseGuard databaseGuard,
            DemoDataRegistry registry,
            ProductService productService,
            ProductRepository productRepository,
            CustomerService customerService,
            CustomerRepository customerRepository,
            SaleService saleService,
            SaleRepository saleRepository,
            SaleItemRepository saleItemRepository,
            PaymentService paymentService,
            PaymentRepository paymentRepository,
            PaymentAllocationRepository paymentAllocationRepository,
            StockMovementRepository stockMovementRepository,
            EntityManager entityManager) {
        this.databaseGuard = databaseGuard;
        this.registry = registry;
        this.productService = productService;
        this.productRepository = productRepository;
        this.customerService = customerService;
        this.customerRepository = customerRepository;
        this.saleService = saleService;
        this.saleRepository = saleRepository;
        this.saleItemRepository = saleItemRepository;
        this.paymentService = paymentService;
        this.paymentRepository = paymentRepository;
        this.paymentAllocationRepository = paymentAllocationRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public DemoDataSummary seed() {
        databaseGuard.verifySafeTarget();
        registry.ensureTable();
        List<Long> productIds = registry.findIds(DemoDataRecordType.PRODUCT);
        List<Long> customerIds = registry.findIds(DemoDataRecordType.CUSTOMER);
        List<Long> saleIds = registry.findIds(DemoDataRecordType.SALE);
        List<Long> paymentIds = registry.findIds(DemoDataRecordType.PAYMENT);
        if (hasAnyRecords(productIds, customerIds, saleIds, paymentIds)) {
            if (isCompleteDataset(productIds, customerIds, saleIds, paymentIds)) {
                return summarize("already exists");
            }
            throw new DemoDataSafetyException(
                    "A partial demo dataset is registered. Reset it before generating demo data again.");
        }
        if (unregisteredDemoRowsExist()) {
            throw new DemoDataSafetyException(
                    "Demo-looking records exist without registry entries. Refusing to duplicate or overwrite them.");
        }

        List<Product> products = createProducts();
        List<Customer> customers = createCustomers();
        for (int index = 0; index < EXPECTED_SALES; index++) {
            SaleResponse sale = createSale(index, customers.get(index % EXPECTED_CUSTOMERS), products);
            registry.record(DemoDataRecordType.SALE, sale.id());
            registerPaymentsForSale(sale.id());
        }
        int paymentNumber = 1;
        for (int customerIndex : CUSTOMERS_WITH_SUBSEQUENT_PAYMENTS) {
            paymentNumber = createSubsequentPayments(
                    customers.get(customerIndex), customerIndex, paymentNumber);
        }
        customerService.deactivateCustomer(customers.get(18).getId());
        customerService.deactivateCustomer(customers.get(19).getId());
        entityManager.flush();

        DemoDataSummary summary = summarize("generated");
        verifyGeneratedDataset(summary);
        return summary;
    }

    @Transactional
    public DemoDataSummary reset() {
        databaseGuard.verifySafeTarget();
        registry.ensureTable();

        List<Long> productIds = registry.findIds(DemoDataRecordType.PRODUCT);
        List<Long> customerIds = registry.findIds(DemoDataRecordType.CUSTOMER);
        List<Long> saleIds = registry.findIds(DemoDataRecordType.SALE);
        List<Long> paymentIds = registry.findIds(DemoDataRecordType.PAYMENT);
        verifyResetDoesNotTouchUnregisteredRecords(productIds, customerIds, saleIds, paymentIds);

        if (!saleIds.isEmpty()) {
            paymentAllocationRepository.deleteBySaleIdIn(saleIds);
            saleItemRepository.deleteBySaleIdIn(saleIds);
            saleRepository.deleteAllByIdInBatch(saleIds);
        }
        if (!paymentIds.isEmpty()) {
            paymentAllocationRepository.deleteByPaymentIdIn(paymentIds);
            paymentRepository.deleteAllByIdInBatch(paymentIds);
        }
        if (!productIds.isEmpty()) {
            stockMovementRepository.deleteByProductIdIn(productIds);
            productRepository.deleteAllByIdInBatch(productIds);
        }
        if (!customerIds.isEmpty()) {
            customerRepository.deleteAllByIdInBatch(customerIds);
        }
        entityManager.flush();
        entityManager.clear();
        registry.clear();
        return summarize("reset");
    }

    private List<Product> createProducts() {
        List<Product> products = new ArrayList<>();
        for (ProductSeed seed : PRODUCT_SEEDS) {
            ProductForm form = new ProductForm();
            form.setName(seed.name());
            form.setBrand(PRODUCT_BRAND);
            form.setUnit(seed.unit());
            form.setQuantity(new BigDecimal(seed.openingQuantity()));
            form.setSellingPrice(new BigDecimal(seed.sellingPrice()));
            form.setLowStockThreshold(new BigDecimal(seed.lowStockThreshold()));
            Product product = productService.createProduct(form);
            registry.record(DemoDataRecordType.PRODUCT, product.getId());
            products.add(product);
        }
        return List.copyOf(products);
    }

    private List<Customer> createCustomers() {
        List<Customer> customers = new ArrayList<>();
        for (int index = 0; index < CUSTOMER_SEEDS.size(); index++) {
            CustomerSeed seed = CUSTOMER_SEEDS.get(index);
            String village = seed.village();
            CustomerForm form = new CustomerForm();
            form.setName("Demo " + seed.name());
            form.setPhone(String.format(java.util.Locale.ROOT, "000000%04d", index + 1));
            form.setVillage(village);
            form.setAddress("Demo Lane, " + village);
            form.setNotes(CUSTOMER_NOTE_PREFIX
                    + " Fictional test profile; not a real person or phone number. Do not contact.");
            Customer customer = customerService.createCustomer(form);
            registry.record(DemoDataRecordType.CUSTOMER, customer.getId());
            customers.add(customer);
        }
        return List.copyOf(customers);
    }

    private SaleResponse createSale(int index, Customer customer, List<Product> products) {
        int lineCount = 1 + index % 4;
        SaleRequest request = new SaleRequest();
        request.setCustomerId(customer.getId());
        request.setRequestKey(SALE_REQUEST_PREFIX + String.format(java.util.Locale.ROOT, "%02d", index + 1));
        List<SaleItemRequest> items = new ArrayList<>();
        BigDecimal total = ZERO;
        for (int line = 0; line < lineCount; line++) {
            Product product = products.get(Math.floorMod(index * 3 + line * 7, products.size()));
            BigDecimal quantity = new BigDecimal("1.000")
                    .add(new BigDecimal("0.500").multiply(BigDecimal.valueOf((index + line) % 3)));
            SaleItemRequest item = new SaleItemRequest();
            item.setProductId(product.getId());
            item.setQuantity(quantity);
            items.add(item);
            total = total.add(product.getSellingPrice().multiply(quantity)
                    .setScale(2, RoundingMode.HALF_UP));
        }
        request.setItems(items);
        BigDecimal initialPayment = switch (index % 4) {
            case 0 -> total;
            case 1 -> percentage(total, 40);
            case 3 -> percentage(total, 30);
            default -> ZERO;
        };
        request.setAmountPaid(initialPayment);
        request.setPaymentMethod(initialPayment.signum() == 0
                ? null
                : index % 2 == 0 ? PaymentMethod.CASH : PaymentMethod.UPI);
        SaleCreationResult result = saleService.createSale(request);
        if (!result.created()) {
            throw new DemoDataSafetyException("A demo sale request key already exists outside the registered dataset");
        }
        return result.sale();
    }

    private void registerPaymentsForSale(Long saleId) {
        for (Long paymentId : paymentAllocationRepository.findPaymentIdsBySaleId(saleId)) {
            registry.record(DemoDataRecordType.PAYMENT, paymentId);
        }
    }

    private int createSubsequentPayments(Customer customer, int customerIndex, int paymentNumber) {
        List<OutstandingInvoice> invoices = paymentService.findOutstandingInvoices(customer.getId());
        if (invoices.size() < 2) {
            throw new DemoDataSafetyException("Expected two outstanding demo invoices for " + customer.getName());
        }

        BigDecimal firstAmount = invoices.getFirst().outstandingAmount()
                .add(percentage(invoices.get(1).outstandingAmount(), 25));
        recordPayment(customer.getId(), firstAmount, paymentNumber++);

        BigDecimal remaining = totalOutstanding(paymentService.findOutstandingInvoices(customer.getId()));
        BigDecimal secondAmount = percentage(remaining, 25);
        if (secondAmount.signum() <= 0 || secondAmount.compareTo(remaining) >= 0) {
            throw new DemoDataSafetyException("Could not prepare a partial demo payment for " + customer.getName());
        }
        recordPayment(customer.getId(), secondAmount, paymentNumber++);

        if (CUSTOMERS_TO_SETTLE.contains(customerIndex)) {
            BigDecimal finalBalance = totalOutstanding(paymentService.findOutstandingInvoices(customer.getId()));
            if (finalBalance.signum() > 0) {
                recordPayment(customer.getId(), finalBalance, paymentNumber++);
            }
        }
        return paymentNumber;
    }

    private void recordPayment(Long customerId, BigDecimal amount, int paymentNumber) {
        List<OutstandingInvoice> invoices = paymentService.findOutstandingInvoices(customerId);
        PaymentForm form = new PaymentForm();
        form.setCustomerId(customerId);
        form.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        form.setPaymentDate(LocalDateTime.now());
        form.setPaymentMethod(paymentNumber % 2 == 0 ? PaymentMethod.UPI : PaymentMethod.CASH);
        form.setTransactionReference("DEMO-PAY-" + String.format(java.util.Locale.ROOT, "%02d", paymentNumber));
        form.setNotes(CUSTOMER_NOTE_PREFIX + " Synthetic demo payment; not a real transaction.");
        form.setRequestKey(PAYMENT_REQUEST_PREFIX
                + String.format(java.util.Locale.ROOT, "%02d", paymentNumber));
        form.setAutoAllocate(true);
        form.setAllocations(allocationsFor(invoices, form.getAmount()));

        PaymentView payment = paymentService.recordPayment(form);
        registry.record(DemoDataRecordType.PAYMENT, payment.id());
    }

    private List<PaymentAllocationForm> allocationsFor(
            List<OutstandingInvoice> invoices,
            BigDecimal paymentAmount) {
        List<PaymentAllocationForm> allocations = new ArrayList<>();
        BigDecimal amountRemaining = paymentAmount;
        for (OutstandingInvoice invoice : invoices) {
            PaymentAllocationForm allocation = new PaymentAllocationForm();
            allocation.setSaleId(invoice.saleId());
            BigDecimal allocated = amountRemaining.min(invoice.outstandingAmount());
            allocation.setAmount(allocated);
            allocations.add(allocation);
            amountRemaining = amountRemaining.subtract(allocated).setScale(2, RoundingMode.HALF_UP);
        }
        if (amountRemaining.signum() != 0) {
            throw new DemoDataSafetyException("Demo payment amount exceeds available invoice balances");
        }
        return allocations;
    }

    private void verifyResetDoesNotTouchUnregisteredRecords(
            List<Long> productIds,
            List<Long> customerIds,
            List<Long> saleIds,
            List<Long> paymentIds) {
        Set<Long> demoSaleIdSet = new HashSet<>(saleIds);
        Set<Long> demoPaymentIdSet = new HashSet<>(paymentIds);

        if (!customerIds.isEmpty()
                && saleRepository.findByCustomerIdIn(customerIds).stream()
                        .anyMatch(sale -> !demoSaleIdSet.contains(sale.getId()))) {
            throw resetBlocked();
        }
        if (!customerIds.isEmpty()
                && paymentRepository.findByCustomerIdIn(customerIds).stream()
                        .anyMatch(payment -> !demoPaymentIdSet.contains(payment.getId()))) {
            throw resetBlocked();
        }
        if (!productIds.isEmpty()
                && saleItemRepository.countNonDemoSalesUsingProducts(
                        productIds, SALE_REQUEST_PREFIX) > 0) {
            throw resetBlocked();
        }
        if (!saleIds.isEmpty()
                && paymentAllocationRepository.findBySaleIdIn(saleIds).stream()
                        .anyMatch(allocation -> !demoPaymentIdSet.contains(allocation.getPayment().getId()))) {
            throw resetBlocked();
        }
        if (!paymentIds.isEmpty()
                && paymentAllocationRepository.findByPaymentIdIn(paymentIds).stream()
                        .anyMatch(allocation -> !demoSaleIdSet.contains(allocation.getSale().getId()))) {
            throw resetBlocked();
        }
    }

    private DemoDataSafetyException resetBlocked() {
        return new DemoDataSafetyException(
                "Reset stopped because unregistered sales or payments reference demo records. "
                        + "No records were deleted.");
    }

    private DemoDataSummary summarize(String outcome) {
        List<Long> productIds = registry.findIds(DemoDataRecordType.PRODUCT);
        List<Long> customerIds = registry.findIds(DemoDataRecordType.CUSTOMER);
        List<Long> saleIds = registry.findIds(DemoDataRecordType.SALE);
        List<Long> paymentIds = registry.findIds(DemoDataRecordType.PAYMENT);
        entityManager.flush();

        BigDecimal totalSales = saleIds.isEmpty()
                ? ZERO
                : saleRepository.sumCompletedSalesByIds(saleIds, SaleStatus.COMPLETED);
        BigDecimal totalPayments = saleIds.isEmpty()
                ? ZERO
                : paymentAllocationRepository.sumValidAllocatedPaymentsForSales(
                        saleIds, SaleStatus.COMPLETED);
        BigDecimal outstanding = totalSales.subtract(totalPayments).setScale(2, RoundingMode.HALF_UP);
        Set<Long> demoProductIds = new HashSet<>(productIds);
        int lowStockCount = (int) productService.findLowStockProducts().stream()
                .filter(product -> demoProductIds.contains(product.getId()))
                .count();

        List<DemoDataSummary.CustomerBalance> outstandingCustomers = customerIds.stream()
                .map(customerService::getCustomerProfile)
                .filter(profile -> profile.financialSummary().outstandingBalance().signum() > 0)
                .map(profile -> new DemoDataSummary.CustomerBalance(
                        profile.customer().name(),
                        profile.financialSummary().outstandingBalance()))
                .toList();

        long saleItemCount = saleIds.isEmpty() ? 0 : saleItemRepository.countBySaleIdIn(saleIds);
        return new DemoDataSummary(
                outcome,
                productIds.size(),
                customerIds.size(),
                saleIds.size(),
                saleItemCount,
                paymentIds.size(),
                totalSales,
                totalPayments,
                outstanding,
                lowStockCount,
                outstandingCustomers);
    }

    private void verifyGeneratedDataset(DemoDataSummary summary) {
        if (summary.productsCreated() != EXPECTED_PRODUCTS
                || summary.customersCreated() != EXPECTED_CUSTOMERS
                || summary.salesCreated() != EXPECTED_SALES
                || summary.paymentsCreated() != EXPECTED_PAYMENTS
                || summary.lowStockProducts() < 3
                || summary.lowStockProducts() > 4
                || summary.outstandingCredit().signum() < 0) {
            throw new DemoDataSafetyException(
                    "Generated demo data failed consistency checks; the transaction will be rolled back");
        }
    }

    private boolean unregisteredDemoRowsExist() {
        return !productRepository.findByBrand(PRODUCT_BRAND).isEmpty()
                || !customerRepository.findByNotesStartingWith(CUSTOMER_NOTE_PREFIX).isEmpty()
                || !saleRepository.findByRequestKeyStartingWith(SALE_REQUEST_PREFIX).isEmpty()
                || !paymentRepository.findByRequestKeyStartingWith(PAYMENT_REQUEST_PREFIX).isEmpty();
    }

    private boolean hasAnyRecords(
            List<Long> productIds,
            List<Long> customerIds,
            List<Long> saleIds,
            List<Long> paymentIds) {
        return !productIds.isEmpty() || !customerIds.isEmpty() || !saleIds.isEmpty() || !paymentIds.isEmpty();
    }

    private boolean isCompleteDataset(
            List<Long> productIds,
            List<Long> customerIds,
            List<Long> saleIds,
            List<Long> paymentIds) {
        return productIds.size() == EXPECTED_PRODUCTS
                && customerIds.size() == EXPECTED_CUSTOMERS
                && saleIds.size() == EXPECTED_SALES
                && paymentIds.size() == EXPECTED_PAYMENTS;
    }

    private BigDecimal totalOutstanding(List<OutstandingInvoice> invoices) {
        return invoices.stream()
                .map(OutstandingInvoice::outstandingAmount)
                .reduce(ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentage(BigDecimal amount, int percent) {
        return amount.multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private record ProductSeed(
            String name,
            String unit,
            String sellingPrice,
            String openingQuantity,
            String lowStockThreshold) {
    }

    private record CustomerSeed(String name, String village) {
    }
}
