package com.agroshop.sales;

public class SaleNotFoundException extends RuntimeException {

    public SaleNotFoundException(Long saleId) {
        super("Sale " + saleId + " was not found");
    }
}
