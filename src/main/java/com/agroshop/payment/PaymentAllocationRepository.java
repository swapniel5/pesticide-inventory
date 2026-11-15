package com.agroshop.payment;

import java.math.BigDecimal;
import java.util.List;

import com.agroshop.sales.SaleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {

    @Query("""
            select new com.agroshop.payment.SaleAllocationTotal(a.sale.id, sum(a.allocatedAmount))
            from PaymentAllocation a
            where a.sale.id in :saleIds
              and a.sale.status = :completedStatus
              and a.sale.customer.id = :customerId
              and a.payment.customer.id = :customerId
              and a.payment.amount > 0
              and a.allocatedAmount > 0
            group by a.sale.id
            """)
    List<SaleAllocationTotal> sumValidAllocationsBySale(
            @Param("customerId") Long customerId,
            @Param("saleIds") List<Long> saleIds,
            @Param("completedStatus") SaleStatus completedStatus);

    @Query("""
            select new com.agroshop.payment.PaymentAllocationView(
                a.payment.id, a.sale.id, a.sale.invoiceNumber, a.sale.totalAmount, a.allocatedAmount
            )
            from PaymentAllocation a
            where a.payment.id in :paymentIds
              and a.sale.status = :completedStatus
              and a.payment.customer.id = a.sale.customer.id
              and a.payment.amount > 0
              and a.allocatedAmount > 0
            order by a.sale.createdAt asc, a.sale.id asc
            """)
    List<PaymentAllocationView> findPaymentAllocations(
            @Param("paymentIds") List<Long> paymentIds,
            @Param("completedStatus") SaleStatus completedStatus);

    @Query("""
            select coalesce(sum(a.allocatedAmount), 0)
            from PaymentAllocation a
            where a.sale.customer.id = :customerId
              and a.sale.status = :completedStatus
              and a.payment.customer.id = a.sale.customer.id
              and a.payment.amount > 0
              and a.allocatedAmount > 0
            """)
    BigDecimal sumValidAllocatedPaymentsForCustomer(
            @Param("customerId") Long customerId,
            @Param("completedStatus") SaleStatus completedStatus);

    @Query("""
            select coalesce(sum(a.allocatedAmount), 0)
            from PaymentAllocation a
            where a.sale.status = :completedStatus
              and a.payment.customer.id = a.sale.customer.id
              and a.payment.amount > 0
              and a.allocatedAmount > 0
            """)
    BigDecimal sumValidAllocatedPayments(@Param("completedStatus") SaleStatus completedStatus);

    @Query("""
            select coalesce(sum(a.allocatedAmount), 0)
            from PaymentAllocation a
            where a.sale.id in :saleIds
              and a.sale.status = :completedStatus
              and a.payment.customer.id = a.sale.customer.id
              and a.payment.amount > 0
              and a.allocatedAmount > 0
            """)
    BigDecimal sumValidAllocatedPaymentsForSales(
            @Param("saleIds") List<Long> saleIds,
            @Param("completedStatus") SaleStatus completedStatus);

    List<PaymentAllocation> findBySaleIdIn(List<Long> saleIds);

    List<PaymentAllocation> findByPaymentIdIn(List<Long> paymentIds);

    @Query("select a.payment.id from PaymentAllocation a where a.sale.id = :saleId")
    List<Long> findPaymentIdsBySaleId(@Param("saleId") Long saleId);

    @Modifying
    @Transactional
    @Query("delete from PaymentAllocation a where a.sale.id in :saleIds")
    void deleteBySaleIdIn(@Param("saleIds") List<Long> saleIds);

    @Modifying
    @Transactional
    @Query("delete from PaymentAllocation a where a.payment.id in :paymentIds")
    void deleteByPaymentIdIn(@Param("paymentIds") List<Long> paymentIds);

    @Query("""
            select coalesce(sum(a.allocatedAmount), 0)
            from PaymentAllocation a
            where a.sale.id = :saleId
              and a.sale.status = :completedStatus
              and a.payment.customer.id = a.sale.customer.id
              and a.payment.amount > 0
              and a.allocatedAmount > 0
            """)
    BigDecimal sumValidAllocatedPaymentsForSale(
            @Param("saleId") Long saleId,
            @Param("completedStatus") SaleStatus completedStatus);
}
