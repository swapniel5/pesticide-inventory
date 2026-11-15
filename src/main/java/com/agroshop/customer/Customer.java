package com.agroshop.customer;

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
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 14)
    private String phone;

    @Column(nullable = false, length = 100)
    private String village;

    @Column(length = 300)
    private String address;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Customer() {
    }

    public Customer(String name, String phone, String village, String address, String notes) {
        this.name = name;
        this.phone = phone;
        this.village = village;
        this.address = address;
        this.notes = notes;
        this.active = true;
    }

    public void updateDetails(String name, String phone, String village, String address, String notes) {
        this.name = name;
        this.phone = phone;
        this.village = village;
        this.address = address;
        this.notes = notes;
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

    public String getPhone() {
        return phone;
    }

    public String getVillage() {
        return village;
    }

    public String getAddress() {
        return address;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isDemoData() {
        return notes != null && notes.startsWith("[AGROSHOP DEMO DATA]");
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
