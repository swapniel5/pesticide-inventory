package com.agroshop.receipt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import com.agroshop.customer.Customer;
import com.agroshop.customer.CustomerRepository;
import com.agroshop.payment.Payment;
import com.agroshop.payment.PaymentAllocation;
import com.agroshop.payment.PaymentAllocationRepository;
import com.agroshop.payment.PaymentMethod;
import com.agroshop.payment.PaymentRepository;
import com.agroshop.product.Product;
import com.agroshop.product.ProductRepository;
import com.agroshop.sales.Sale;
import com.agroshop.sales.SaleItem;
import com.agroshop.sales.SaleItemRepository;
import com.agroshop.sales.SaleRepository;
import com.agroshop.sales.SaleStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:receipt-integration;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "shop.name=AgroShop Demo",
        "shop.address=Village Market Road",
        "shop.phone=+919876543210"
})
@AutoConfigureMockMvc
class ReceiptIntegrationTest {

    @Autowired
    private ReceiptService receiptService;

    @Autowired
    private WhatsAppLinkBuilder whatsAppLinkBuilder;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private SaleItemRepository saleItemRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentAllocationRepository paymentAllocationRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsFullyPaidReceiptFromPaymentAllocations() {
        Sale sale = createSale("Farmer One", "9876543210", "Neem Spray", "2", "50.00");
        createPayment(sale, "100.00");

        ReceiptView receipt = receiptService.getReceipt(sale.getId());

        assertThat(receipt.shopName()).isEqualTo("AgroShop Demo");
        assertThat(receipt.shopAddress()).isEqualTo("Village Market Road");
        assertThat(receipt.invoiceTotal()).isEqualByComparingTo("100.00");
        assertThat(receipt.totalPaid()).isEqualByComparingTo("100.00");
        assertThat(receipt.outstandingBalance()).isEqualByComparingTo("0.00");
        assertThat(receipt.paymentStatus()).isEqualTo("PAID");
        assertThat(receipt.items()).singleElement()
                .extracting(ReceiptItem::unitPrice)
                .isEqualTo(new BigDecimal("50.00"));
    }

    @Test
    void calculatesPartiallyPaidReceiptBalance() {
        Sale sale = createSale("Farmer Two", "9876543211", "Crop Care", "4", "25.00");
        createPayment(sale, "30.00");

        ReceiptView receipt = receiptService.getReceipt(sale.getId());

        assertThat(receipt.totalPaid()).isEqualByComparingTo("30.00");
        assertThat(receipt.outstandingBalance()).isEqualByComparingTo("70.00");
        assertThat(receipt.paymentStatus()).isEqualTo("PARTIALLY PAID");
    }

    @Test
    void marksReceiptUnpaidWhenThereAreNoPaymentAllocations() {
        Sale sale = createSale("Farmer Three", "9876543212", "Seed Treatment", "1", "75.00");

        ReceiptView receipt = receiptService.getReceipt(sale.getId());

        assertThat(receipt.totalPaid()).isEqualByComparingTo("0.00");
        assertThat(receipt.outstandingBalance()).isEqualByComparingTo("75.00");
        assertThat(receipt.paymentStatus()).isEqualTo("UNPAID");
    }

    @Test
    void sumsMultipleSubsequentPaymentsOnceAndUsesHistoricalItemSnapshots() {
        Sale sale = createSale("Asha & Sons", "9876543213", "Neem + Soap / कृषि", "2", "50.00");
        createPayment(sale, "25.00");
        createPayment(sale, "35.00");
        Product product = sale.getItems().getFirst().getProduct();
        product.updateDetails("Renamed product", null, "packet", new BigDecimal("99.00"), BigDecimal.ONE);
        productRepository.save(product);

        ReceiptView receipt = receiptService.getReceipt(sale.getId());

        assertThat(receipt.totalPaid()).isEqualByComparingTo("60.00");
        assertThat(receipt.outstandingBalance()).isEqualByComparingTo("40.00");
        assertThat(receipt.items().getFirst().productName()).isEqualTo("Neem + Soap / कृषि");
        assertThat(receipt.items().getFirst().unitPrice()).isEqualByComparingTo("50.00");
    }

    @Test
    @Transactional
    void identifiesCancelledSaleAndNeverShowsANegativeBalance() {
        Sale sale = createSale("Farmer Four", "9876543214", "Fungicide", "1", "100.00");
        createPayment(sale, "100.00");
        entityManager.flush();
        entityManager.createNativeQuery("update sales set status = 'CANCELLED' where id = :saleId")
                .setParameter("saleId", sale.getId())
                .executeUpdate();
        entityManager.clear();

        ReceiptView receipt = receiptService.getReceipt(sale.getId());

        assertThat(receipt.saleStatus()).isEqualTo(SaleStatus.CANCELLED);
        assertThat(receipt.paymentStatus()).isEqualTo("CANCELLED");
        assertThat(receipt.totalPaid()).isEqualByComparingTo("0.00");
        assertThat(receipt.outstandingBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void normalizesIndianAndInternationalPhoneNumbersAndRejectsInvalidNumbers() {
        assertThat(whatsAppLinkBuilder.normalizePhone("98765 43210")).contains("919876543210");
        assertThat(whatsAppLinkBuilder.normalizePhone("+91 98765-43210")).contains("919876543210");
        assertThat(whatsAppLinkBuilder.normalizePhone("+1 202-555-0182")).contains("12025550182");
        assertThat(whatsAppLinkBuilder.normalizePhone("1234567890")).isEmpty();
        assertThat(whatsAppLinkBuilder.normalizePhone("phone unavailable")).isEmpty();
    }

    @Test
    void urlEncodesTheCompleteMessageAndSpecialCustomerAndProductNames() {
        Sale sale = createSale("Asha & Sons", "9876543213", "Neem + Soap / कृषि", "2", "50.00");
        ReceiptView receipt = receiptService.getReceipt(sale.getId());
        String url = receipt.whatsappUrl();

        assertThat(url).startsWith("https://wa.me/919876543213?text=");
        String encodedMessage = url.substring(url.indexOf("?text=") + "?text=".length());
        String message = URLDecoder.decode(encodedMessage, StandardCharsets.UTF_8);
        assertThat(message)
                .contains("AgroShop Demo")
                .contains(sale.getInvoiceNumber())
                .contains("Asha & Sons")
                .contains("Neem + Soap / कृषि")
                .contains("Unit price:")
                .contains("Line total:")
                .contains("Invoice total:")
                .contains("Total paid:")
                .contains("Outstanding balance:");
    }

    @Test
    void handlesInvalidCustomerMobileWithoutGeneratingAShareLink() {
        Sale sale = createSale("Farmer Five", "1234567890", "Crop Mix", "1", "20.00");

        assertThat(receiptService.getReceipt(sale.getId()).whatsappUrl()).isNull();
    }

    @Test
    void disablesWhatsAppSharingForDemoCustomersEvenIfPhoneLooksValid() {
        Sale sale = createSale(
                "Demo Farmer",
                "9876543210",
                "Demo Product",
                "1",
                "20.00",
                "[AGROSHOP DEMO DATA] Fictional test profile; not a real person.");

        assertThat(receiptService.getReceipt(sale.getId()).whatsappUrl()).isNull();
    }

    @Test
    @WithMockUser
    void rendersPrintableReceiptWithStatusAndWhatsAppAction() throws Exception {
        Sale sale = createSale("Farmer Six", "9876543215", "Foliar Feed", "2", "40.00");
        createPayment(sale, "20.00");

        mockMvc.perform(get("/sales/" + sale.getId() + "/receipt"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("AgroShop Demo")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(sale.getInvoiceNumber())))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("PARTIALLY PAID")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Print receipt")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Share on WhatsApp")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("window.print()")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Crafted by Swapnil Jejurkar")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("All Rights Reserved.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "class=\"no-print border-top mt-5 py-3\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        ".no-print { display: none !important; }")));
    }

    private Sale createSale(
            String customerName,
            String phone,
            String productName,
            String quantity,
            String unitPrice) {
        return createSale(customerName, phone, productName, quantity, unitPrice, null);
    }

    private Sale createSale(
            String customerName,
            String phone,
            String productName,
            String quantity,
            String unitPrice,
            String customerNotes) {
        Customer customer = customerRepository.save(new Customer(
                customerName, phone, "Rampur", "Market Road", customerNotes));
        Product product = productRepository.save(new Product(
                productName,
                "AgroBrand",
                "bottle",
                new BigDecimal("100"),
                new BigDecimal(unitPrice),
                BigDecimal.ONE));
        BigDecimal itemQuantity = new BigDecimal(quantity);
        BigDecimal price = new BigDecimal(unitPrice);
        BigDecimal lineTotal = itemQuantity.multiply(price).setScale(2);
        Sale sale = saleRepository.save(new Sale(
                customer,
                lineTotal,
                "receipt-" + UUID.randomUUID(),
                "INV-RECEIPT-" + UUID.randomUUID()));
        saleItemRepository.save(new SaleItem(sale, product, itemQuantity, price, lineTotal));
        return saleRepository.findById(sale.getId()).orElseThrow();
    }

    private void createPayment(Sale sale, String amount) {
        BigDecimal paymentAmount = new BigDecimal(amount);
        Payment payment = paymentRepository.save(new Payment(
                sale.getCustomer(),
                paymentAmount,
                PaymentMethod.CASH,
                LocalDateTime.now().withSecond(0).withNano(0),
                null,
                "Receipt test",
                null));
        paymentAllocationRepository.save(new PaymentAllocation(payment, sale, paymentAmount));
    }
}
