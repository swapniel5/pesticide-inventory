package com.agroshop.inventory;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    @EntityGraph(attributePaths = "product")
    List<StockMovement> findByProductIdOrderByCreatedAtDesc(Long productId);

    @EntityGraph(attributePaths = "product")
    List<StockMovement> findTop100ByOrderByCreatedAtDesc();

    @Modifying
    @Transactional
    @Query("delete from StockMovement movement where movement.product.id in :productIds")
    void deleteByProductIdIn(@Param("productIds") List<Long> productIds);
}
