package com.agroshop.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.agroshop.inventory.InsufficientStockException;
import com.agroshop.inventory.MovementType;
import com.agroshop.inventory.StockMovement;
import com.agroshop.inventory.StockMovementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void createsProductAndRecordsOpeningStock() {
        stubProductSave();
        Product product = productService.createProduct(form("12"));

        assertEquals("Garden Spray", product.getName());
        assertEquals(new BigDecimal("12"), product.getQuantity());
        ArgumentCaptor<StockMovement> movement = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(movement.capture());
        assertEquals(MovementType.OPENING, movement.getValue().getMovementType());
        assertEquals(new BigDecimal("12"), movement.getValue().getQuantity());
    }

    @Test
    void updatesProductDetailsWithoutChangingStock() {
        stubProductSave();
        Product product = product("8");
        when(productRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(product));
        ProductForm changes = form("200");
        changes.setName("Updated Spray");

        Product updated = productService.updateProduct(7L, changes);

        assertEquals("Updated Spray", updated.getName());
        assertEquals(new BigDecimal("8"), updated.getQuantity());
        verify(productRepository).findByIdForUpdate(7L);
        verify(stockMovementRepository, never()).save(any(StockMovement.class));
    }

    @Test
    void retrievesProductById() {
        Product expected = product("3");
        when(productRepository.findById(7L)).thenReturn(Optional.of(expected));

        assertSame(expected, productService.getProduct(7L));
    }

    @Test
    void addsStockAndRecordsMovement() {
        stubProductSave();
        Product product = product("4");
        when(productRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(product));

        Product updated = productService.recordStockMovement(
                7L, MovementType.PURCHASE, new BigDecimal("2.5"), "Restock");

        assertEquals(new BigDecimal("6.5"), updated.getQuantity());
        ArgumentCaptor<StockMovement> movement = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(movement.capture());
        assertEquals(MovementType.PURCHASE, movement.getValue().getMovementType());
        assertEquals(new BigDecimal("2.5"), movement.getValue().getQuantity());
    }

    @Test
    void reducesStockAndRecordsAdjustment() {
        stubProductSave();
        Product product = product("4");
        when(productRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(product));

        Product updated = productService.recordStockMovement(
                7L, MovementType.ADJUSTMENT_OUT, new BigDecimal("1.5"), "Damaged");

        assertEquals(new BigDecimal("2.5"), updated.getQuantity());
        ArgumentCaptor<StockMovement> movement = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(movement.capture());
        assertEquals(MovementType.ADJUSTMENT_OUT, movement.getValue().getMovementType());
    }

    @Test
    void rejectsStockReductionThatWouldMakeQuantityNegative() {
        Product product = product("2");
        when(productRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(product));

        assertThrows(InsufficientStockException.class, () -> productService.recordStockMovement(
                7L, MovementType.ADJUSTMENT_OUT, new BigDecimal("2.1"), "Adjustment"));

        assertEquals(new BigDecimal("2"), product.getQuantity());
        verify(productRepository, never()).save(any(Product.class));
        verify(stockMovementRepository, never()).save(any(StockMovement.class));
    }

    @Test
    void returnsProductsFoundByLowStockQuery() {
        List<Product> lowStock = List.of(product("1"));
        when(productRepository.findLowStockProducts()).thenReturn(lowStock);

        assertSame(lowStock, productService.findLowStockProducts());
    }

    private ProductForm form(String quantity) {
        ProductForm form = new ProductForm();
        form.setName("Garden Spray");
        form.setBrand("Agro");
        form.setUnit("bottle");
        form.setQuantity(new BigDecimal(quantity));
        form.setSellingPrice(new BigDecimal("25.50"));
        form.setLowStockThreshold(new BigDecimal("3"));
        return form;
    }

    private Product product(String quantity) {
        return new Product(
                "Garden Spray", "Agro", "bottle", new BigDecimal(quantity),
                new BigDecimal("25.50"), new BigDecimal("3"));
    }

    private void stubProductSave() {
        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }
}
