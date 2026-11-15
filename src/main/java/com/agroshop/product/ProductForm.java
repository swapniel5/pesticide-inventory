package com.agroshop.product;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ProductForm {

    @NotBlank(message = "Product name is required")
    @Size(max = 150, message = "Product name must be 150 characters or fewer")
    private String name;

    @Size(max = 100, message = "Brand must be 100 characters or fewer")
    private String brand;

    @NotBlank(message = "Unit is required")
    @Size(max = 50, message = "Unit must be 50 characters or fewer")
    private String unit;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0.0", message = "Quantity cannot be negative")
    private BigDecimal quantity = BigDecimal.ZERO;

    @NotNull(message = "Selling price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Selling price must be greater than zero")
    private BigDecimal sellingPrice;

    @NotNull(message = "Low-stock threshold is required")
    @DecimalMin(value = "0.0", message = "Low-stock threshold cannot be negative")
    private BigDecimal lowStockThreshold = BigDecimal.ZERO;

    public static ProductForm from(Product product) {
        ProductForm form = new ProductForm();
        form.name = product.getName();
        form.brand = product.getBrand();
        form.unit = product.getUnit();
        form.quantity = product.getQuantity();
        form.sellingPrice = product.getSellingPrice();
        form.lowStockThreshold = product.getLowStockThreshold();
        return form;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getSellingPrice() {
        return sellingPrice;
    }

    public void setSellingPrice(BigDecimal sellingPrice) {
        this.sellingPrice = sellingPrice;
    }

    public BigDecimal getLowStockThreshold() {
        return lowStockThreshold;
    }

    public void setLowStockThreshold(BigDecimal lowStockThreshold) {
        this.lowStockThreshold = lowStockThreshold;
    }
}
