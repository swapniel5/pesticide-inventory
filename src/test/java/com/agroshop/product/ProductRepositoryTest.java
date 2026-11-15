package com.agroshop.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Test
    void findsActiveProductsAtOrBelowTheirLowStockThreshold() {
        Product belowThreshold = new Product(
                "Below", null, "bottle", new BigDecimal("2"), BigDecimal.ONE, new BigDecimal("3"));
        Product atThreshold = new Product(
                "At", null, "bottle", new BigDecimal("3"), BigDecimal.ONE, new BigDecimal("3"));
        Product aboveThreshold = new Product(
                "Above", null, "bottle", new BigDecimal("4"), BigDecimal.ONE, new BigDecimal("3"));
        Product inactiveLowStock = new Product(
                "Inactive", null, "bottle", BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE);
        inactiveLowStock.deactivate();
        productRepository.saveAll(List.of(belowThreshold, atThreshold, aboveThreshold, inactiveLowStock));

        List<Product> lowStockProducts = productRepository.findLowStockProducts();

        assertThat(lowStockProducts)
                .extracting(Product::getName)
                .containsExactly("At", "Below");
    }
}
