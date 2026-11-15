package com.agroshop.customer;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    List<Customer> findByNameContainingIgnoreCaseOrPhoneContainingIgnoreCaseOrVillageContainingIgnoreCaseOrderByNameAsc(
            String name, String phone, String village);

    List<Customer> findByActiveTrueOrderByNameAsc();

    List<Customer> findByNotesStartingWith(String prefix);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.id = :id")
    Optional<Customer> findByIdForUpdate(@Param("id") Long id);

    @Query(value = """
            select c.id as "customerId",
                   c.name as "customerName",
                   c.phone as "phone",
                   c.village as "village",
                   coalesce(sales_total.total_sales, 0) - coalesce(payment_total.total_paid, 0)
                       as "outstandingBalance",
                   c.active as "active"
            from customers c
            left join (
                select s.customer_id, sum(s.total_amount) as total_sales
                from sales s
                where s.status = :completedStatus
                group by s.customer_id
            ) sales_total on sales_total.customer_id = c.id
            left join (
                select s.customer_id, sum(a.allocated_amount) as total_paid
                from payment_allocations a
                join sales s on s.id = a.sale_id
                join payments p on p.id = a.payment_id
                where s.status = :completedStatus
                  and p.customer_id = s.customer_id
                  and p.amount > 0
                  and a.allocated_amount > 0
                group by s.customer_id
            ) payment_total on payment_total.customer_id = c.id
            where coalesce(sales_total.total_sales, 0) - coalesce(payment_total.total_paid, 0) > 0
            order by "outstandingBalance" desc, c.name asc, c.id asc
            limit 5
            """, nativeQuery = true)
    List<CustomerOutstandingSummary> findTopOutstandingBalances(
            @Param("completedStatus") String completedStatus);
}
