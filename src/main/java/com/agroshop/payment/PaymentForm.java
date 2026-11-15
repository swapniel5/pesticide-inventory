package com.agroshop.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PaymentForm {

    @NotNull(message = "Select a customer")
    private Long customerId;

    @NotNull(message = "Enter the payment amount")
    @DecimalMin(value = "0.01", message = "Payment amount must be greater than zero")
    @Digits(integer = 12, fraction = 2, message = "Payment amount can have up to 12 whole and 2 decimal digits")
    private BigDecimal amount;

    @NotNull(message = "Select the payment date and time")
    private LocalDateTime paymentDate;

    @NotNull(message = "Select a payment method")
    private PaymentMethod paymentMethod;

    @Size(max = 120, message = "Transaction reference cannot exceed 120 characters")
    private String transactionReference;

    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    private String notes;

    @NotBlank
    @Size(max = 64)
    private String requestKey;

    private boolean autoAllocate;
    private List<PaymentAllocationForm> allocations = new ArrayList<>();

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDateTime getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(LocalDateTime paymentDate) {
        this.paymentDate = paymentDate;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public void setTransactionReference(String transactionReference) {
        this.transactionReference = transactionReference;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getRequestKey() {
        return requestKey;
    }

    public void setRequestKey(String requestKey) {
        this.requestKey = requestKey;
    }

    public boolean isAutoAllocate() {
        return autoAllocate;
    }

    public void setAutoAllocate(boolean autoAllocate) {
        this.autoAllocate = autoAllocate;
    }

    public List<PaymentAllocationForm> getAllocations() {
        return allocations;
    }

    public void setAllocations(List<PaymentAllocationForm> allocations) {
        this.allocations = allocations == null ? new ArrayList<>() : allocations;
    }
}
