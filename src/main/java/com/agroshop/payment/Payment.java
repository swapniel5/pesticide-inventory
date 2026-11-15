package com.agroshop.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.agroshop.customer.Customer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Check;

@Entity
@Table(
        name = "payments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payments_request_key",
                columnNames = "request_key"))
@Check(constraints = "amount > 0")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    @Column(nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20, updatable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "payment_date", nullable = false, updatable = false)
    private LocalDateTime paymentDate;

    @Column(name = "transaction_reference", length = 120, updatable = false)
    private String transactionReference;

    @Column(length = 500, updatable = false)
    private String notes;

    @Column(name = "request_key", length = 64, updatable = false)
    private String requestKey;

    protected Payment() {
    }

    public Payment(Customer customer, BigDecimal amount, PaymentMethod paymentMethod, String notes) {
        this(customer, amount, paymentMethod, null, null, notes, null);
    }

    public Payment(
            Customer customer,
            BigDecimal amount,
            PaymentMethod paymentMethod,
            LocalDateTime paymentDate,
            String transactionReference,
            String notes,
            String requestKey) {
        this.customer = customer;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.paymentDate = paymentDate;
        this.transactionReference = transactionReference;
        this.notes = notes;
        this.requestKey = requestKey;
    }

    @PrePersist
    void setPaymentDate() {
        if (paymentDate == null) {
            paymentDate = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public LocalDateTime getPaymentDate() {
        return paymentDate;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public String getNotes() {
        return notes;
    }

    public String getRequestKey() {
        return requestKey;
    }
}
