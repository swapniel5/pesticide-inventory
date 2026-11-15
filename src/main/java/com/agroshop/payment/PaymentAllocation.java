package com.agroshop.payment;

import java.math.BigDecimal;

import com.agroshop.sales.Sale;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Check;

@Entity
@Table(
        name = "payment_allocations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payment_allocations_payment_sale",
                columnNames = {"payment_id", "sale_id"}))
@Check(constraints = "allocated_amount > 0")
public class PaymentAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, updatable = false)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false, updatable = false)
    private Sale sale;

    @Column(name = "allocated_amount", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal allocatedAmount;

    protected PaymentAllocation() {
    }

    public PaymentAllocation(Payment payment, Sale sale, BigDecimal allocatedAmount) {
        this.payment = payment;
        this.sale = sale;
        this.allocatedAmount = allocatedAmount;
    }

    public Long getId() {
        return id;
    }

    public Payment getPayment() {
        return payment;
    }

    public Sale getSale() {
        return sale;
    }

    public BigDecimal getAllocatedAmount() {
        return allocatedAmount;
    }
}
