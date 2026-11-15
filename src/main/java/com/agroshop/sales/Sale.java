package com.agroshop.sales;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.agroshop.customer.Customer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Check;

@Entity
@Table(
        name = "sales",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_sales_invoice_number", columnNames = "invoice_number"),
                @UniqueConstraint(name = "uk_sales_request_key", columnNames = "request_key")
        },
        indexes = @Index(name = "idx_sales_created_at", columnList = "created_at"))
@Check(constraints = "total_amount > 0")
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sales_id_generator")
    @SequenceGenerator(name = "sales_id_generator", sequenceName = "sales_id_seq", allocationSize = 1)
    private Long id;

    @Column(name = "invoice_number", nullable = false, length = 64)
    private String invoiceNumber;

    @Column(name = "request_key", nullable = false, length = 64)
    private String requestKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SaleStatus status;

    @OneToMany(mappedBy = "sale")
    private List<SaleItem> items = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Sale() {
    }

    public Sale(Customer customer, BigDecimal totalAmount, String requestKey, String pendingInvoiceNumber) {
        this.customer = customer;
        this.totalAmount = totalAmount;
        this.requestKey = requestKey;
        this.status = SaleStatus.COMPLETED;
        this.invoiceNumber = pendingInvoiceNumber;
    }

    public void assignInvoiceNumber(String invoiceNumber) {
        if (this.invoiceNumber == null || !this.invoiceNumber.startsWith("TMP-")) {
            throw new IllegalStateException("Invoice number cannot be replaced");
        }
        this.invoiceNumber = invoiceNumber;
    }

    public void addItem(SaleItem item) {
        items.add(item);
    }

    @PrePersist
    void setCreationTimestamps() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public String getRequestKey() {
        return requestKey;
    }

    public Customer getCustomer() {
        return customer;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public SaleStatus getStatus() {
        return status;
    }

    public List<SaleItem> getItems() {
        return List.copyOf(items);
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
