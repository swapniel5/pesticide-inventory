package com.agroshop.sales;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.agroshop.customer.Customer;
import com.agroshop.customer.CustomerRepository;
import com.agroshop.inventory.InsufficientStockException;
import com.agroshop.inventory.MovementType;
import com.agroshop.inventory.StockMovement;
import com.agroshop.inventory.StockMovementRepository;
import com.agroshop.payment.Payment;
import com.agroshop.payment.PaymentAllocation;
import com.agroshop.payment.PaymentAllocationRepository;
import com.agroshop.payment.PaymentRepository;
import com.agroshop.product.Product;
import com.agroshop.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class SaleService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final int CURRENCY_SCALE = 2;
    private static final int QUANTITY_SCALE = 3;
    private static final int MAX_AMOUNT_INTEGER_DIGITS = 12;

    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;
    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;

    public SaleService(
            CustomerRepository customerRepository,
            ProductRepository productRepository,
            StockMovementRepository stockMovementRepository,
            SaleRepository saleRepository,
            SaleItemRepository saleItemRepository,
            PaymentRepository paymentRepository,
            PaymentAllocationRepository paymentAllocationRepository) {
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.saleRepository = saleRepository;
        this.saleItemRepository = saleItemRepository;
        this.paymentRepository = paymentRepository;
        this.paymentAllocationRepository = paymentAllocationRepository;
    }

    public SaleCreationResult createSale(SaleRequest request) {
        String requestKey = requireRequestKey(request);
        Sale existingSale = findExistingSale(requestKey);
        if (existingSale != null) {
            return new SaleCreationResult(toResponse(existingSale), false);
        }

        validateRequest(request);
        Customer customer = customerRepository.findByIdForUpdate(request.getCustomerId())
                .orElseThrow(() -> new SaleValidationException("Selected customer was not found"));
        if (!customer.isActive()) {
            throw new SaleValidationException("Inactive customers cannot make new purchases");
        }

        List<Long> productIds = request.getItems().stream()
                .map(SaleItemRequest::getProductId)
                .distinct()
                .sorted()
                .toList();
        Map<Long, Product> products = new LinkedHashMap<>();
        for (Long productId : productIds) {
            Product product = productRepository.findByIdForUpdate(productId)
                    .orElseThrow(() -> new SaleValidationException("Selected product " + productId + " was not found"));
            products.put(productId, product);
        }

        existingSale = findExistingSale(requestKey);
        if (existingSale != null) {
            return new SaleCreationResult(toResponse(existingSale), false);
        }

        List<PreparedItem> preparedItems = new ArrayList<>();
        BigDecimal totalAmount = ZERO;
        for (SaleItemRequest itemRequest : request.getItems()) {
            Product product = products.get(itemRequest.getProductId());
            if (!product.isActive()) {
                throw new SaleValidationException("Inactive products cannot be sold: " + product.getName());
            }
            if (product.getSellingPrice() == null || product.getSellingPrice().signum() <= 0) {
                throw new SaleValidationException("Product selling price must be greater than zero");
            }

            BigDecimal quantity = normalizeQuantity(itemRequest.getQuantity());
            BigDecimal unitPrice = product.getSellingPrice().setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);
            BigDecimal lineTotal = quantity.multiply(unitPrice).setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);
            ensureSupportedAmount(lineTotal);
            totalAmount = totalAmount.add(lineTotal).setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);
            ensureSupportedAmount(totalAmount);
            preparedItems.add(new PreparedItem(product, quantity, unitPrice, lineTotal));
        }

        BigDecimal amountPaid = normalizePayment(request.getAmountPaid());
        if (amountPaid.compareTo(totalAmount) > 0) {
            throw new SaleValidationException("Initial payment cannot exceed the invoice total");
        }
        if (amountPaid.signum() > 0 && request.getPaymentMethod() == null) {
            throw new SaleValidationException("Select a payment method for the initial payment");
        }

        Sale sale = saleRepository.save(new Sale(
                customer, totalAmount, requestKey, "TMP-" + UUID.randomUUID()));
        sale.assignInvoiceNumber(String.format(
                Locale.ROOT, "INV-%d-%04d", LocalDate.now().getYear(), sale.getId()));
        saleRepository.flush();

        for (PreparedItem prepared : preparedItems) {
            Product product = prepared.product();
            if (product.getQuantity().compareTo(prepared.quantity()) < 0) {
                throw new InsufficientStockException(prepared.quantity(), product.getQuantity());
            }
            product.reduceStock(prepared.quantity());
            productRepository.save(product);

            SaleItem saleItem = new SaleItem(
                    sale, product, prepared.quantity(), prepared.unitPrice(), prepared.lineTotal());
            sale.addItem(saleItem);
            saleItemRepository.save(saleItem);
            stockMovementRepository.save(new StockMovement(
                    product,
                    MovementType.SALE,
                    prepared.quantity(),
                    "Sale " + sale.getInvoiceNumber()));
        }

        if (amountPaid.signum() > 0) {
            Payment payment = paymentRepository.save(new Payment(
                    customer,
                    amountPaid,
                    request.getPaymentMethod(),
                    "Initial payment for " + sale.getInvoiceNumber()));
            paymentAllocationRepository.save(new PaymentAllocation(payment, sale, amountPaid));
        }

        return new SaleCreationResult(toResponse(sale), true);
    }

    @Transactional(readOnly = true)
    public List<SaleResponse> findSales() {
        return saleRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SaleResponse getSale(Long id) {
        Sale sale = saleRepository.findById(id).orElseThrow(() -> new SaleNotFoundException(id));
        return toResponse(sale);
    }

    private SaleResponse toResponse(Sale sale) {
        BigDecimal amountPaid = sale.getStatus() == SaleStatus.COMPLETED
                ? paymentAllocationRepository.sumValidAllocatedPaymentsForSale(sale.getId(), SaleStatus.COMPLETED)
                : ZERO;
        if (amountPaid == null) {
            amountPaid = ZERO;
        }
        amountPaid = amountPaid.setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);
        return SaleResponse.from(sale, amountPaid);
    }

    private Sale findExistingSale(String requestKey) {
        return saleRepository.findByRequestKey(requestKey).orElse(null);
    }

    private void validateRequest(SaleRequest request) {
        if (request == null || request.getCustomerId() == null) {
            throw new SaleValidationException("Customer is mandatory");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new SaleValidationException("At least one sale item is required");
        }
        if (request.getItems().size() > 100) {
            throw new SaleValidationException("A sale can contain at most 100 product lines");
        }
        if (request.getItems().stream().anyMatch(item -> item == null || item.getProductId() == null)) {
            throw new SaleValidationException("Select a product for every sale item");
        }
        if (request.getAmountPaid() == null) {
            throw new SaleValidationException("Initial payment amount is required");
        }
    }

    private String requireRequestKey(SaleRequest request) {
        String requestKey = request == null ? null : request.getRequestKey();
        if (!StringUtils.hasText(requestKey)) {
            throw new SaleValidationException("Sale submission key is required");
        }
        String normalized = requestKey.trim();
        if (normalized.length() > 64) {
            throw new SaleValidationException("Sale submission key is too long");
        }
        return normalized;
    }

    private BigDecimal normalizeQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new SaleValidationException("Product quantity must be greater than zero");
        }
        try {
            return quantity.setScale(QUANTITY_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new SaleValidationException("Quantity can have up to three decimal places");
        }
    }

    private BigDecimal normalizePayment(BigDecimal amountPaid) {
        if (amountPaid == null || amountPaid.signum() < 0) {
            throw new SaleValidationException("Initial payment cannot be negative");
        }
        try {
            return amountPaid.setScale(CURRENCY_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new SaleValidationException("Initial payment can have up to two decimal places");
        }
    }

    private void ensureSupportedAmount(BigDecimal amount) {
        if (amount.precision() - amount.scale() > MAX_AMOUNT_INTEGER_DIGITS) {
            throw new SaleValidationException("Sale amount exceeds the supported limit");
        }
    }

    private record PreparedItem(Product product, BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
    }
}
