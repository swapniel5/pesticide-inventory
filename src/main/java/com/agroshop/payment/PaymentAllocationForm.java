package com.agroshop.payment;

import java.math.BigDecimal;

public class PaymentAllocationForm {

    private Long saleId;
    private BigDecimal amount;

    public Long getSaleId() {
        return saleId;
    }

    public void setSaleId(Long saleId) {
        this.saleId = saleId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
