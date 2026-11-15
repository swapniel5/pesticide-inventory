package com.agroshop.sales;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.agroshop.payment.PaymentStatus;

public record SaleResponse(
        Long id,
        String invoiceNumber,
        Long customerId,
        String customerName,
        String customerPhone,
        List<SaleItemResponse> items,
        BigDecimal totalAmount,
        BigDecimal amountPaid,
        BigDecimal outstandingBalance,
        SaleStatus status,
        LocalDateTime createdAt) {

    public static SaleResponse from(Sale sale, BigDecimal amountPaid) {
        List<SaleItemResponse> itemResponses = sale.getItems().stream()
                .map(SaleItemResponse::from)
                .toList();
        BigDecimal normalizedAmountPaid = sale.getStatus() == SaleStatus.COMPLETED
                ? (amountPaid == null ? BigDecimal.ZERO.setScale(2) : amountPaid)
                : BigDecimal.ZERO.setScale(2);
        BigDecimal outstandingBalance = sale.getStatus() == SaleStatus.COMPLETED
                ? sale.getTotalAmount().subtract(normalizedAmountPaid)
                : BigDecimal.ZERO.setScale(2);
        return new SaleResponse(
                sale.getId(),
                sale.getInvoiceNumber(),
                sale.getCustomer().getId(),
                sale.getCustomer().getName(),
                sale.getCustomer().getPhone(),
                itemResponses,
                sale.getTotalAmount(),
                normalizedAmountPaid,
                outstandingBalance,
                sale.getStatus(),
                sale.getCreatedAt());
    }

    public String getPaymentStatusLabel() {
        return PaymentStatus.forInvoice(status, totalAmount, amountPaid).label();
    }
}
