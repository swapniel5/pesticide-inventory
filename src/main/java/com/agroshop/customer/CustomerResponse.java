package com.agroshop.customer;

import java.time.LocalDateTime;

public record CustomerResponse(
        Long id,
        String name,
        String phone,
        String village,
        String address,
        String notes,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getName(),
                customer.getPhone(),
                customer.getVillage(),
                customer.getAddress(),
                customer.getNotes(),
                customer.isActive(),
                customer.getCreatedAt(),
                customer.getUpdatedAt());
    }
}
