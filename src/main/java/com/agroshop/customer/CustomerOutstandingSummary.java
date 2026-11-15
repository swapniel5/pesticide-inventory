package com.agroshop.customer;

import java.math.BigDecimal;

public interface CustomerOutstandingSummary {

    Long getCustomerId();

    String getCustomerName();

    String getPhone();

    String getVillage();

    BigDecimal getOutstandingBalance();

    boolean isActive();
}
