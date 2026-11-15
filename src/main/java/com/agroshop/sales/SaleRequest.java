package com.agroshop.sales;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.agroshop.payment.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class SaleRequest {

    @NotNull(message = "Select a customer")
    private Long customerId;

    @NotEmpty(message = "Add at least one product to the sale")
    @Size(max = 100, message = "A sale can contain at most 100 product lines")
    private List<@NotNull @Valid SaleItemRequest> items = new ArrayList<>();

    @NotNull(message = "Enter the amount received, or zero")
    @DecimalMin(value = "0.00", message = "Initial payment cannot be negative")
    @Digits(integer = 12, fraction = 2, message = "Initial payment can have up to two decimal places")
    private BigDecimal amountPaid = BigDecimal.ZERO;

    private PaymentMethod paymentMethod;

    @NotBlank(message = "Sale submission key is required")
    @Size(max = 64, message = "Sale submission key is too long")
    private String requestKey;

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public List<SaleItemRequest> getItems() {
        return items;
    }

    public void setItems(List<SaleItemRequest> items) {
        this.items = items;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public void setAmountPaid(BigDecimal amountPaid) {
        this.amountPaid = amountPaid;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getRequestKey() {
        return requestKey;
    }

    public void setRequestKey(String requestKey) {
        this.requestKey = requestKey;
    }
}
