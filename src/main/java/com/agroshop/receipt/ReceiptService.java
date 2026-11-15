package com.agroshop.receipt;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.agroshop.payment.PaymentAllocationRepository;
import com.agroshop.payment.PaymentStatus;
import com.agroshop.sales.Sale;
import com.agroshop.sales.SaleItem;
import com.agroshop.sales.SaleNotFoundException;
import com.agroshop.sales.SaleRepository;
import com.agroshop.sales.SaleStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReceiptService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final SaleRepository saleRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final ShopProperties shopProperties;
    private final WhatsAppLinkBuilder whatsAppLinkBuilder;

    public ReceiptService(
            SaleRepository saleRepository,
            PaymentAllocationRepository paymentAllocationRepository,
            ShopProperties shopProperties,
            WhatsAppLinkBuilder whatsAppLinkBuilder) {
        this.saleRepository = saleRepository;
        this.paymentAllocationRepository = paymentAllocationRepository;
        this.shopProperties = shopProperties;
        this.whatsAppLinkBuilder = whatsAppLinkBuilder;
    }

    public ReceiptView getReceipt(Long saleId) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new SaleNotFoundException(saleId));
        BigDecimal totalPaid = sale.getStatus() == SaleStatus.COMPLETED
                ? paymentAllocationRepository.sumValidAllocatedPaymentsForSale(saleId, SaleStatus.COMPLETED)
                : ZERO;
        if (totalPaid == null) {
            totalPaid = ZERO;
        }
        totalPaid = totalPaid.setScale(2, RoundingMode.HALF_UP);

        BigDecimal outstanding = sale.getStatus() == SaleStatus.COMPLETED
                ? sale.getTotalAmount().subtract(totalPaid).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
                : ZERO;
        String paymentStatus = PaymentStatus.forInvoice(
                sale.getStatus(), sale.getTotalAmount(), totalPaid).label();
        List<ReceiptItem> items = sale.getItems().stream()
                .map(this::toReceiptItem)
                .toList();

        ReceiptView receipt = new ReceiptView(
                sale.getId(),
                shopProperties.name(),
                shopProperties.address(),
                shopProperties.phone(),
                sale.getInvoiceNumber(),
                sale.getCreatedAt(),
                sale.getCustomer().getName(),
                sale.getCustomer().getPhone(),
                sale.getCustomer().getVillage(),
                items,
                sale.getTotalAmount(),
                totalPaid,
                outstanding,
                sale.getStatus(),
                paymentStatus,
                null);
        String whatsAppUrl = sale.getCustomer().isDemoData()
                ? null
                : whatsAppLinkBuilder.buildUrl(sale.getCustomer().getPhone(), receipt).orElse(null);
        return receipt.withWhatsappUrl(whatsAppUrl);
    }

    private ReceiptItem toReceiptItem(SaleItem item) {
        return new ReceiptItem(
                item.getProductNameSnapshot(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getLineTotal());
    }
}
