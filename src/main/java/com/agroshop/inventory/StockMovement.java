package com.agroshop.inventory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.agroshop.product.Product;
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

@Entity
@Table(name = "stock_movements")
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MovementType movementType;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(length = 500)
    private String remarks;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected StockMovement() {
    }

    public StockMovement(Product product, MovementType movementType, BigDecimal quantity, String remarks) {
        this.product = product;
        this.movementType = movementType;
        this.quantity = quantity;
        this.remarks = remarks;
    }

    @PrePersist
    void setCreatedAt() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public MovementType getMovementType() {
        return movementType;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public String getRemarks() {
        return remarks;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
