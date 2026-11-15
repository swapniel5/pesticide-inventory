package com.agroshop.receipt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shop")
public record ShopProperties(String name, String address, String phone) {

    public ShopProperties {
        name = name == null || name.isBlank() ? "AgroShop" : name.trim();
        address = address == null ? "" : address.trim();
        phone = phone == null ? "" : phone.trim();
    }
}
