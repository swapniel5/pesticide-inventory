package com.agroshop.payment;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;

public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    @Override
    @EntityGraph(attributePaths = "customer")
    List<Payment> findAll(Specification<Payment> specification, Sort sort);

    @EntityGraph(attributePaths = "customer")
    java.util.Optional<Payment> findById(Long id);

    java.util.Optional<Payment> findByRequestKey(String requestKey);

    List<Payment> findByCustomerIdIn(List<Long> customerIds);

    List<Payment> findByRequestKeyStartingWith(String prefix);

    @Query("""
            select new com.agroshop.payment.RecentPaymentSummary(
                p.id,
                p.transactionReference,
                p.customer.id,
                p.customer.name,
                p.paymentDate,
                p.amount,
                p.paymentMethod
            )
            from Payment p
            order by p.paymentDate desc, p.id desc
            """)
    List<RecentPaymentSummary> findRecentPayments(Pageable pageable);
}
