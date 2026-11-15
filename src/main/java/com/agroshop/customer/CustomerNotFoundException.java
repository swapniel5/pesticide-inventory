package com.agroshop.customer;

public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException(Long customerId) {
        super("Customer " + customerId + " was not found");
    }
}
