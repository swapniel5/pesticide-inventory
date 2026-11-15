package com.agroshop.receipt;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.Locale;
import java.util.Optional;

import com.agroshop.sales.SaleStatus;
import org.springframework.stereotype.Component;

@Component
public class WhatsAppLinkBuilder {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);

    public Optional<String> normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return Optional.empty();
        }
        String trimmed = phone.trim();
        boolean explicitInternationalPrefix = trimmed.startsWith("+");
        String digits = trimmed.replaceAll("[\\s+\\-]", "");
        if (!digits.matches("\\d+")) {
            return Optional.empty();
        }
        if (digits.length() == 10) {
            return digits.matches("[6-9]\\d{9}")
                    ? Optional.of("91" + digits)
                    : Optional.empty();
        }
        if (digits.length() == 12 && digits.startsWith("91")) {
            return digits.substring(2).matches("[6-9]\\d{9}")
                    ? Optional.of(digits)
                    : Optional.empty();
        }
        if ((explicitInternationalPrefix || digits.length() > 10)
                && digits.matches("[1-9]\\d{7,14}")) {
            return Optional.of(digits);
        }
        return Optional.empty();
    }

    public Optional<String> buildUrl(String phone, ReceiptView receipt) {
        return normalizePhone(phone).map(normalizedPhone ->
                "https://wa.me/" + normalizedPhone + "?text="
                        + URLEncoder.encode(buildMessage(receipt), StandardCharsets.UTF_8));
    }

    private String buildMessage(ReceiptView receipt) {
        StringBuilder message = new StringBuilder()
                .append(receipt.shopName())
                .append("\nInvoice: ").append(receipt.invoiceNumber())
                .append("\nDate: ").append(DATE_FORMAT.format(receipt.saleDate()))
                .append("\nCustomer: ").append(receipt.customerName());
        if (!receipt.shopAddress().isBlank()) {
            message.append("\n").append(receipt.shopAddress());
        }
        if (!receipt.shopPhone().isBlank()) {
            message.append("\nShop mobile: ").append(receipt.shopPhone());
        }
        message.append("\n\nItems:");
        for (ReceiptItem item : receipt.items()) {
            message.append("\n- ").append(item.productName())
                    .append(" | Qty: ").append(item.quantity().stripTrailingZeros().toPlainString())
                    .append(" | Unit price: ").append(currency(item.unitPrice()))
                    .append(" | Line total: ").append(currency(item.lineTotal()));
        }
        message.append("\n\nInvoice total: ").append(currency(receipt.invoiceTotal()))
                .append("\nTotal paid: ").append(currency(receipt.totalPaid()))
                .append("\nOutstanding balance: ").append(currency(receipt.outstandingBalance()));
        if (receipt.saleStatus() == SaleStatus.CANCELLED) {
            message.append("\nInvoice status: CANCELLED");
        }
        return message.toString();
    }

    private String currency(BigDecimal amount) {
        NumberFormat formatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"));
        formatter.setCurrency(Currency.getInstance("INR"));
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);
        return formatter.format(amount);
    }
}
