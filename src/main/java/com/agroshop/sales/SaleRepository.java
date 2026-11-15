package com.agroshop.sales;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.agroshop.customer.CustomerPurchaseHistoryItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    @Query("""
            select s
            from Sale s
            where s.customer.id = :customerId
              and s.status = :completedStatus
            order by s.createdAt asc, s.id asc
            """)
    List<Sale> findCompletedSalesByCustomer(
            @Param("customerId") Long customerId,
            @Param("completedStatus") SaleStatus completedStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s
            from Sale s
            where s.customer.id = :customerId
              and s.status = :completedStatus
            order by s.createdAt asc, s.id asc
            """)
    List<Sale> findCompletedSalesByCustomerForUpdate(
            @Param("customerId") Long customerId,
            @Param("completedStatus") SaleStatus completedStatus);

    @EntityGraph(attributePaths = {"customer", "items", "items.product"})
    List<Sale> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"customer", "items", "items.product"})
    Optional<Sale> findById(Long id);

    @EntityGraph(attributePaths = {"customer", "items", "items.product"})
    Optional<Sale> findByRequestKey(String requestKey);

    List<Sale> findByRequestKeyStartingWith(String prefix);

    List<Sale> findByCustomerIdIn(List<Long> customerIds);

    @Query("""
            select new com.agroshop.customer.CustomerPurchaseHistoryItem(
                s.id,
                s.invoiceNumber,
                s.createdAt,
                s.totalAmount,
                coalesce((
                    select sum(a.allocatedAmount)
                    from PaymentAllocation a
                    where a.sale.id = s.id
                      and s.status = :completedStatus
                      and a.payment.customer.id = s.customer.id
                      and a.payment.amount > 0
                      and a.allocatedAmount > 0
                ), 0),
                s.status
            )
            from Sale s
            where s.customer.id = :customerId
            order by s.createdAt desc, s.id desc
            """)
    List<CustomerPurchaseHistoryItem> findCustomerPurchaseHistory(
            @Param("customerId") Long customerId,
            @Param("completedStatus") SaleStatus completedStatus);

    @Query("""
            select coalesce(sum(s.totalAmount), 0)
            from Sale s
            where s.customer.id = :customerId
              and s.status = :completedStatus
            """)
    BigDecimal sumCompletedSalesForCustomer(
            @Param("customerId") Long customerId,
            @Param("completedStatus") SaleStatus completedStatus);

    @Query("""
            select coalesce(sum(s.totalAmount), 0)
            from Sale s
            where s.status = :completedStatus
            """)
    BigDecimal sumCompletedSales(@Param("completedStatus") SaleStatus completedStatus);

    @Query("""
            select coalesce(sum(s.totalAmount), 0)
            from Sale s
            where s.id in :saleIds
              and s.status = :completedStatus
            """)
    BigDecimal sumCompletedSalesByIds(
            @Param("saleIds") List<Long> saleIds,
            @Param("completedStatus") SaleStatus completedStatus);

    @Query("""
            select new com.agroshop.sales.RecentSaleSummary(
                s.id,
                s.invoiceNumber,
                s.createdAt,
                s.customer.name,
                s.totalAmount,
                coalesce((
                    select sum(a.allocatedAmount)
                    from PaymentAllocation a
                    where a.sale.id = s.id
                      and a.sale.status = :completedStatus
                      and a.payment.customer.id = s.customer.id
                      and a.payment.amount > 0
                      and a.allocatedAmount > 0
                ), 0),
                s.status
            )
            from Sale s
            where s.status = :completedStatus
            order by s.createdAt desc, s.id desc
            """)
    List<RecentSaleSummary> findRecentCompletedSales(
            @Param("completedStatus") SaleStatus completedStatus,
            Pageable pageable);
}
