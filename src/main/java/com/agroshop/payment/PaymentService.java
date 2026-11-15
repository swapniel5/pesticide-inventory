package com.agroshop.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.agroshop.customer.Customer;
import com.agroshop.customer.CustomerNotFoundException;
import com.agroshop.customer.CustomerRepository;
import com.agroshop.sales.Sale;
import com.agroshop.sales.SaleRepository;
import com.agroshop.sales.SaleStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class PaymentService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final int CURRENCY_SCALE = 2;
    private static final int MAX_AMOUNT_INTEGER_DIGITS = 12;

    private final CustomerRepository customerRepository;
    private final SaleRepository saleRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;

    public PaymentService(
            CustomerRepository customerRepository,
            SaleRepository saleRepository,
            PaymentRepository paymentRepository,
            PaymentAllocationRepository paymentAllocationRepository) {
        this.customerRepository = customerRepository;
        this.saleRepository = saleRepository;
        this.paymentRepository = paymentRepository;
        this.paymentAllocationRepository = paymentAllocationRepository;
    }

    public PaymentView recordPayment(PaymentForm form) {
        validateRequiredFields(form);
        String requestKey = normalizeRequestKey(form.getRequestKey());
        Payment existing = paymentRepository.findByRequestKey(requestKey).orElse(null);
        if (existing != null) {
            return existingSubmission(existing, form);
        }

        BigDecimal amount = normalizeAmount(form.getAmount());
        LocalDateTime paymentDate = form.getPaymentDate();
        PaymentMethod paymentMethod = form.getPaymentMethod();
        String transactionReference = trimToNull(form.getTransactionReference());
        String notes = trimToNull(form.getNotes());

        Customer customer = customerRepository.findByIdForUpdate(form.getCustomerId())
                .orElseThrow(() -> new CustomerNotFoundException(form.getCustomerId()));
        existing = paymentRepository.findByRequestKey(requestKey).orElse(null);
        if (existing != null) {
            return existingSubmission(existing, form);
        }

        List<Sale> lockedSales = saleRepository.findCompletedSalesByCustomerForUpdate(
                customer.getId(), SaleStatus.COMPLETED);
        if (lockedSales.isEmpty()) {
            throw new PaymentValidationException("This customer has no completed invoices to allocate a payment to");
        }

        Map<Long, Sale> salesById = lockedSales.stream()
                .collect(Collectors.toMap(Sale::getId, Function.identity(), (first, ignored) -> first,
                        LinkedHashMap::new));
        Map<Long, BigDecimal> paidBySale = allocationTotals(customer.getId(), lockedSales);
        Map<Long, BigDecimal> outstandingBySale = new LinkedHashMap<>();
        BigDecimal totalOutstanding = ZERO;
        for (Sale sale : lockedSales) {
            BigDecimal paid = paidBySale.getOrDefault(sale.getId(), ZERO);
            BigDecimal outstanding = sale.getTotalAmount()
                    .subtract(paid)
                    .setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);
            if (outstanding.signum() < 0) {
                throw new PaymentValidationException(
                        "Invoice " + sale.getInvoiceNumber() + " has an invalid negative balance");
            }
            outstandingBySale.put(sale.getId(), outstanding);
            totalOutstanding = totalOutstanding.add(outstanding).setScale(CURRENCY_SCALE);
        }
        if (amount.compareTo(totalOutstanding) > 0) {
            throw new PaymentValidationException("Payment cannot exceed the customer's total outstanding balance");
        }

        Map<Long, BigDecimal> allocations;
        Map<Long, BigDecimal> submittedAllocations = readSubmittedAllocations(form.getAllocations(), salesById);
        if (form.isAutoAllocate()) {
            allocations = allocateOldestFirst(lockedSales, outstandingBySale, amount);
            if (!allocations.equals(submittedAllocations)) {
                throw new PaymentValidationException(
                        "Invoice balances changed. Reload the page and review the automatic allocation again");
            }
        } else {
            allocations = submittedAllocations;
            validateManualAllocations(allocations, outstandingBySale, amount);
        }

        Payment payment = paymentRepository.save(new Payment(
                customer,
                amount,
                paymentMethod,
                paymentDate,
                transactionReference,
                notes,
                requestKey));
        List<PaymentInvoiceAllocation> savedAllocations = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> allocation : allocations.entrySet()) {
            Sale sale = salesById.get(allocation.getKey());
            BigDecimal allocatedAmount = allocation.getValue();
            paymentAllocationRepository.save(new PaymentAllocation(payment, sale, allocatedAmount));
            savedAllocations.add(new PaymentInvoiceAllocation(
                    sale.getId(), sale.getInvoiceNumber(), sale.getTotalAmount(), allocatedAmount));
        }
        return toView(payment, savedAllocations);
    }

    @Transactional(readOnly = true)
    public List<OutstandingInvoice> findOutstandingInvoices(Long customerId) {
        customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
        List<Sale> sales = saleRepository.findCompletedSalesByCustomer(customerId, SaleStatus.COMPLETED);
        if (sales.isEmpty()) {
            return List.of();
        }
        Map<Long, BigDecimal> paidBySale = allocationTotals(customerId, sales);
        return sales.stream()
                .map(sale -> {
                    BigDecimal paid = paidBySale.getOrDefault(sale.getId(), ZERO);
                    BigDecimal outstanding = sale.getTotalAmount()
                            .subtract(paid)
                            .setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);
                    if (outstanding.signum() < 0) {
                        throw new PaymentValidationException(
                                "Invoice " + sale.getInvoiceNumber() + " has an invalid negative balance");
                    }
                    return new OutstandingInvoice(
                            sale.getId(),
                            sale.getInvoiceNumber(),
                            sale.getCreatedAt(),
                            sale.getTotalAmount(),
                            paid,
                            outstanding);
                })
                .filter(invoice -> invoice.outstandingAmount().signum() > 0)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentView> findPayments(Long customerId, LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new PaymentValidationException("Start date cannot be after end date");
        }
        if (customerId != null && !customerRepository.existsById(customerId)) {
            throw new CustomerNotFoundException(customerId);
        }
        LocalDateTime from = fromDate == null ? null : fromDate.atStartOfDay();
        LocalDateTime toExclusive = toDate == null ? null : toDate.plusDays(1).atStartOfDay();
        Specification<Payment> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (customerId != null) {
                predicates.add(criteriaBuilder.equal(root.get("customer").get("id"), customerId));
            }
            if (from != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("paymentDate"), from));
            }
            if (toExclusive != null) {
                predicates.add(criteriaBuilder.lessThan(root.get("paymentDate"), toExclusive));
            }
            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
        List<Payment> payments = paymentRepository.findAll(
                specification,
                Sort.by(Sort.Order.desc("paymentDate"), Sort.Order.desc("id")));
        return toViews(payments);
    }

    @Transactional(readOnly = true)
    public List<PaymentView> findCustomerPayments(Long customerId) {
        return findPayments(customerId, null, null);
    }

    @Transactional(readOnly = true)
    public PaymentView getPayment(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
        return toView(payment);
    }

    private List<PaymentView> toViews(List<Payment> payments) {
        if (payments.isEmpty()) {
            return List.of();
        }
        Map<Long, List<PaymentInvoiceAllocation>> allocationsByPayment =
                allocationsByPayment(payments.stream().map(Payment::getId).toList());
        return payments.stream()
                .map(payment -> toView(
                        payment,
                        allocationsByPayment.getOrDefault(payment.getId(), List.of())))
                .toList();
    }

    private PaymentView toView(Payment payment) {
        return toView(payment, allocationsByPayment(List.of(payment.getId()))
                .getOrDefault(payment.getId(), List.of()));
    }

    private PaymentView existingSubmission(Payment existing, PaymentForm form) {
        PaymentView savedPayment = toView(existing);
        Map<Long, BigDecimal> submittedAllocations = readSubmittedAllocations(form.getAllocations());
        Map<Long, BigDecimal> savedAllocations = savedPayment.allocations().stream()
                .collect(Collectors.toMap(
                        PaymentInvoiceAllocation::saleId,
                        PaymentInvoiceAllocation::allocatedAmount));
        if (!existing.getCustomer().getId().equals(form.getCustomerId())
                || existing.getAmount().compareTo(normalizeAmount(form.getAmount())) != 0
                || !existing.getPaymentDate().equals(form.getPaymentDate())
                || existing.getPaymentMethod() != form.getPaymentMethod()
                || !Objects.equals(existing.getTransactionReference(), trimToNull(form.getTransactionReference()))
                || !Objects.equals(existing.getNotes(), trimToNull(form.getNotes()))
                || !savedAllocations.equals(submittedAllocations)) {
            throw new PaymentValidationException("Payment submission key has already been used for different details");
        }
        return savedPayment;
    }

    private PaymentView toView(Payment payment, List<PaymentInvoiceAllocation> allocations) {
        return new PaymentView(
                payment.getId(),
                payment.getCustomer().getId(),
                payment.getCustomer().getName(),
                payment.getAmount(),
                payment.getPaymentMethod(),
                payment.getPaymentDate(),
                payment.getTransactionReference(),
                payment.getNotes(),
                allocations);
    }

    private Map<Long, List<PaymentInvoiceAllocation>> allocationsByPayment(List<Long> paymentIds) {
        if (paymentIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<PaymentInvoiceAllocation>> allocationsByPayment = new LinkedHashMap<>();
        for (PaymentAllocationView allocation :
                paymentAllocationRepository.findPaymentAllocations(paymentIds, SaleStatus.COMPLETED)) {
            allocationsByPayment.computeIfAbsent(allocation.paymentId(), ignored -> new ArrayList<>())
                    .add(new PaymentInvoiceAllocation(
                            allocation.saleId(),
                            allocation.invoiceNumber(),
                            allocation.invoiceTotal(),
                            allocation.allocatedAmount()));
        }
        return allocationsByPayment;
    }

    private Map<Long, BigDecimal> allocationTotals(Long customerId, List<Sale> sales) {
        List<Long> saleIds = sales.stream().map(Sale::getId).toList();
        if (saleIds.isEmpty()) {
            return Map.of();
        }
        return paymentAllocationRepository.sumValidAllocationsBySale(
                        customerId, saleIds, SaleStatus.COMPLETED)
                .stream()
                .collect(Collectors.toMap(
                        SaleAllocationTotal::saleId,
                        total -> total.allocatedAmount().setScale(CURRENCY_SCALE, RoundingMode.HALF_UP)));
    }

    private Map<Long, BigDecimal> readSubmittedAllocations(
            List<PaymentAllocationForm> formAllocations,
            Map<Long, Sale> salesById) {
        Map<Long, BigDecimal> allocations = readSubmittedAllocations(formAllocations);
        if (formAllocations != null && formAllocations.stream()
                .filter(formAllocation -> formAllocation != null && formAllocation.getSaleId() != null)
                .anyMatch(formAllocation -> !salesById.containsKey(formAllocation.getSaleId()))) {
            throw new PaymentValidationException("Invoices must belong to this customer and be completed");
        }
        return allocations;
    }

    private Map<Long, BigDecimal> readSubmittedAllocations(List<PaymentAllocationForm> formAllocations) {
        if (formAllocations == null) {
            throw new PaymentValidationException("Review the invoice allocations before saving");
        }
        Map<Long, BigDecimal> allocations = new LinkedHashMap<>();
        for (PaymentAllocationForm formAllocation : formAllocations) {
            if (formAllocation == null || formAllocation.getSaleId() == null) {
                throw new PaymentValidationException("Select an invoice for every allocation");
            }
            Long saleId = formAllocation.getSaleId();
            if (allocations.containsKey(saleId)) {
                throw new PaymentValidationException("An invoice can only be selected once");
            }
            BigDecimal amount = normalizeAllocation(formAllocation.getAmount());
            if (amount.signum() > 0) {
                allocations.put(saleId, amount);
            }
        }
        return allocations;
    }

    private Map<Long, BigDecimal> allocateOldestFirst(
            List<Sale> oldestFirst,
            Map<Long, BigDecimal> outstandingBySale,
            BigDecimal amount) {
        Map<Long, BigDecimal> allocations = new LinkedHashMap<>();
        BigDecimal amountRemaining = amount;
        for (Sale sale : oldestFirst) {
            if (amountRemaining.signum() == 0) {
                break;
            }
            BigDecimal outstanding = outstandingBySale.get(sale.getId());
            if (outstanding.signum() == 0) {
                continue;
            }
            BigDecimal allocated = amountRemaining.min(outstanding);
            allocations.put(sale.getId(), allocated);
            amountRemaining = amountRemaining.subtract(allocated).setScale(CURRENCY_SCALE);
        }
        if (amountRemaining.signum() != 0) {
            throw new PaymentValidationException("Payment could not be allocated to outstanding invoices");
        }
        return allocations;
    }

    private void validateManualAllocations(
            Map<Long, BigDecimal> allocations,
            Map<Long, BigDecimal> outstandingBySale,
            BigDecimal paymentAmount) {
        BigDecimal totalAllocated = ZERO;
        for (Map.Entry<Long, BigDecimal> allocation : allocations.entrySet()) {
            BigDecimal outstanding = outstandingBySale.get(allocation.getKey());
            if (outstanding.signum() == 0) {
                throw new PaymentValidationException("Fully paid invoices cannot receive another allocation");
            }
            if (allocation.getValue().compareTo(outstanding) > 0) {
                throw new PaymentValidationException("Allocation cannot exceed the invoice outstanding amount");
            }
            totalAllocated = totalAllocated.add(allocation.getValue()).setScale(CURRENCY_SCALE);
        }
        if (totalAllocated.compareTo(paymentAmount) != 0) {
            throw new PaymentValidationException("Total invoice allocations must equal the payment amount");
        }
    }

    private void validateRequiredFields(PaymentForm form) {
        if (form == null || form.getCustomerId() == null) {
            throw new PaymentValidationException("Customer is mandatory");
        }
        if (form.getAmount() == null) {
            throw new PaymentValidationException("Payment amount is mandatory");
        }
        if (form.getPaymentDate() == null) {
            throw new PaymentValidationException("Payment date is mandatory");
        }
        if (form.getPaymentMethod() == null) {
            throw new PaymentValidationException("Payment method is mandatory");
        }
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount.signum() <= 0) {
            throw new PaymentValidationException("Payment amount must be greater than zero");
        }
        BigDecimal normalized = normalizeCurrency(amount, "Payment amount");
        if (normalized.precision() - normalized.scale() > MAX_AMOUNT_INTEGER_DIGITS) {
            throw new PaymentValidationException("Payment amount exceeds the supported limit");
        }
        return normalized;
    }

    private BigDecimal normalizeAllocation(BigDecimal amount) {
        if (amount == null) {
            return ZERO;
        }
        if (amount.signum() < 0) {
            throw new PaymentValidationException("Allocation amounts cannot be negative");
        }
        return normalizeCurrency(amount, "Allocation amount");
    }

    private BigDecimal normalizeCurrency(BigDecimal amount, String label) {
        try {
            return amount.setScale(CURRENCY_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new PaymentValidationException(label + " can have up to two decimal places");
        }
    }

    private String normalizeRequestKey(String requestKey) {
        if (!StringUtils.hasText(requestKey)) {
            throw new PaymentValidationException("Payment submission key is required");
        }
        String normalized = requestKey.trim();
        if (normalized.length() > 64) {
            throw new PaymentValidationException("Payment submission key is too long");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
