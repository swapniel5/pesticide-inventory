package com.agroshop.demodata;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

record DemoDataSummary(
        String outcome,
        int productsCreated,
        int customersCreated,
        int salesCreated,
        long saleItemsCreated,
        int paymentsCreated,
        BigDecimal totalSales,
        BigDecimal totalPayments,
        BigDecimal outstandingCredit,
        int lowStockProducts,
        List<CustomerBalance> customersWithOutstanding) {

    DemoDataSummary {
        customersWithOutstanding = List.copyOf(customersWithOutstanding);
    }

    String report() {
        NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"));
        StringBuilder report = new StringBuilder()
                .append("Demo dataset ").append(outcome)
                .append(System.lineSeparator())
                .append("Products: ").append(productsCreated).append(System.lineSeparator())
                .append("Customers: ").append(customersCreated).append(System.lineSeparator())
                .append("Sales invoices: ").append(salesCreated).append(System.lineSeparator())
                .append("Sale items: ").append(saleItemsCreated).append(System.lineSeparator())
                .append("Payments: ").append(paymentsCreated).append(System.lineSeparator())
                .append("Total sales: ").append(currency.format(totalSales)).append(System.lineSeparator())
                .append("Total payments allocated: ").append(currency.format(totalPayments)).append(System.lineSeparator())
                .append("Total outstanding credit: ").append(currency.format(outstandingCredit)).append(System.lineSeparator())
                .append("Low-stock products: ").append(lowStockProducts).append(System.lineSeparator())
                .append("Customers with outstanding balances:");
        if (customersWithOutstanding.isEmpty()) {
            report.append(System.lineSeparator()).append("- None");
        } else {
            for (CustomerBalance customer : customersWithOutstanding) {
                report.append(System.lineSeparator())
                        .append("- ").append(customer.name())
                        .append(": ").append(currency.format(customer.balance()));
            }
        }
        return report.toString();
    }

    record CustomerBalance(String name, BigDecimal balance) {
    }
}
