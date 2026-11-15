package com.agroshop.product;

import java.math.BigDecimal;
import java.util.List;

import com.agroshop.inventory.InsufficientStockException;
import com.agroshop.inventory.MovementType;
import com.agroshop.inventory.StockMovement;
import com.agroshop.inventory.StockMovementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;

    public ProductService(ProductRepository productRepository, StockMovementRepository stockMovementRepository) {
        this.productRepository = productRepository;
        this.stockMovementRepository = stockMovementRepository;
    }

    public Product createProduct(ProductForm form) {
        Product product = new Product(
                form.getName().trim(),
                trimToNull(form.getBrand()),
                form.getUnit().trim(),
                form.getQuantity(),
                form.getSellingPrice(),
                form.getLowStockThreshold());
        product = productRepository.save(product);
        if (form.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
            stockMovementRepository.save(new StockMovement(
                    product, MovementType.OPENING, form.getQuantity(), "Opening stock"));
        }
        return product;
    }

    @Transactional(readOnly = true)
    public List<Product> findProducts(String search) {
        if (StringUtils.hasText(search)) {
            return productRepository.findByNameContainingIgnoreCaseOrderByNameAsc(search.trim());
        }
        return productRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Product getProduct(Long id) {
        return productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    public Product updateProduct(Long id, ProductForm form) {
        Product product = productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        product.updateDetails(
                form.getName().trim(),
                trimToNull(form.getBrand()),
                form.getUnit().trim(),
                form.getSellingPrice(),
                form.getLowStockThreshold());
        return productRepository.save(product);
    }

    public Product deactivateProduct(Long id) {
        Product product = productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        product.deactivate();
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public List<Product> findLowStockProducts() {
        return productRepository.findLowStockProducts();
    }

    @Transactional(readOnly = true)
    public List<Product> findActiveProducts() {
        return productRepository.findByActiveTrueOrderByNameAsc();
    }

    public Product recordStockMovement(Long productId, MovementType movementType, BigDecimal amount, String remarks) {
        if (movementType == null
                || (movementType != MovementType.PURCHASE
                && movementType != MovementType.ADJUSTMENT_IN
                && movementType != MovementType.ADJUSTMENT_OUT)) {
            throw new IllegalArgumentException("Stock movement must be a purchase or stock adjustment");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Stock movement quantity must be greater than zero");
        }

        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        if (!product.isActive()) {
            throw new IllegalArgumentException("Inactive products cannot receive stock movements");
        }

        if (movementType == MovementType.ADJUSTMENT_OUT) {
            if (product.getQuantity().compareTo(amount) < 0) {
                throw new InsufficientStockException(amount, product.getQuantity());
            }
            product.reduceStock(amount);
        } else {
            product.increaseStock(amount);
        }

        productRepository.save(product);
        stockMovementRepository.save(new StockMovement(product, movementType, amount, trimToNull(remarks)));
        return product;
    }

    @Transactional(readOnly = true)
    public List<StockMovement> getStockMovements(Long productId) {
        getProduct(productId);
        return stockMovementRepository.findByProductIdOrderByCreatedAtDesc(productId);
    }

    @Transactional(readOnly = true)
    public List<StockMovement> getRecentStockMovements() {
        return stockMovementRepository.findTop100ByOrderByCreatedAtDesc();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
