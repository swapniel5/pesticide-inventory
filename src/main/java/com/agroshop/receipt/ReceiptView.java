package com.agroshop.receipt;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.agroshop.sales.SaleStatus;

public record ReceiptView(
        Long saleId,
        String shopName,
        String shopAddress,
        String shopPhone,
        String invoiceNumber,
        LocalDateTime saleDate,
        String customerName,
        String customerPhone,
        String customerVillage,
        List<ReceiptItem> items,
        BigDecimal invoiceTotal,
        BigDecimal totalPaid,
        BigDecimal outstandingBalance,
        SaleStatus saleStatus,
        String paymentStatus,
        String whatsappUrl) {

    public ReceiptView {
        items = List.copyOf(items);
    }

    public ReceiptView withWhatsappUrl(String url) {
        return new ReceiptView(
                saleId,
                shopName,
                shopAddress,
                shopPhone,
                invoiceNumber,
                saleDate,
                customerName,
                customerPhone,
                customerVillage,
                items,
                invoiceTotal,
                totalPaid,
                outstandingBalance,
                saleStatus,
                paymentStatus,
                url);
    }
}
