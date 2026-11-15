package com.agroshop.receipt;

import java.math.BigDecimal;

public record ReceiptItem(String productName, BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
}
