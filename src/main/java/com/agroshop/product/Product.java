package com.agroshop.product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 100)
    private String brand;

    @Column(nullable = false, length = 50)
    private String unit;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal sellingPrice;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal lowStockThreshold;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Product() {
    }

    public Product(String name, String brand, String unit, BigDecimal quantity,
                   BigDecimal sellingPrice, BigDecimal lowStockThreshold) {
        this.name = name;
        this.brand = brand;
        this.unit = unit;
        this.quantity = quantity;
        this.sellingPrice = sellingPrice;
        this.lowStockThreshold = lowStockThreshold;
        this.active = true;
    }

    public void updateDetails(String name, String brand, String unit, BigDecimal sellingPrice,
                              BigDecimal lowStockThreshold) {
        this.name = name;
        this.brand = brand;
        this.unit = unit;
        this.sellingPrice = sellingPrice;
        this.lowStockThreshold = lowStockThreshold;
    }

    public void increaseStock(BigDecimal amount) {
        quantity = quantity.add(amount);
    }

    public void reduceStock(BigDecimal amount) {
        if (quantity.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient stock");
        }
        quantity = quantity.subtract(amount);
    }

    public void deactivate() {
        active = false;
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

    public String getName() {
        return name;
    }

    public String getBrand() {
        return brand;
    }

    public String getUnit() {
        return unit;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getSellingPrice() {
        return sellingPrice;
    }

    public BigDecimal getLowStockThreshold() {
        return lowStockThreshold;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
