package com.agroshop.payment;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(Long id) {
        super("Payment " + id + " was not found");
    }
}
