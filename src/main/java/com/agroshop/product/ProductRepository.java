package com.agroshop.product;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByNameContainingIgnoreCaseOrderByNameAsc(String name);

    List<Product> findByActiveTrueOrderByNameAsc();

    List<Product> findByBrand(String brand);

    @Query("select p from Product p where p.active = true and p.quantity <= p.lowStockThreshold order by p.name")
    List<Product> findLowStockProducts();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);
}
