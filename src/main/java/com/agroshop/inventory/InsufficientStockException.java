package com.agroshop.inventory;

import java.math.BigDecimal;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(BigDecimal requested, BigDecimal available) {
        super("Cannot reduce stock by " + requested + "; only " + available + " is available");
    }
}
