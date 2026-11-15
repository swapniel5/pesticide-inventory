package com.agroshop.sales;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

    List<SaleItem> findBySaleIdOrderByIdAsc(Long saleId);

    List<SaleItem> findBySaleIdIn(List<Long> saleIds);

    List<SaleItem> findByProductIdIn(List<Long> productIds);

    long countBySaleIdIn(List<Long> saleIds);

    @Query("""
            select count(item)
            from SaleItem item
            where item.product.id in :productIds
              and item.sale.requestKey not like concat(:demoPrefix, '%')
            """)
    long countNonDemoSalesUsingProducts(
            @Param("productIds") List<Long> productIds,
            @Param("demoPrefix") String demoPrefix);

    @Modifying
    @Transactional
    @Query("delete from SaleItem item where item.sale.id in :saleIds")
    void deleteBySaleIdIn(@Param("saleIds") List<Long> saleIds);
}
