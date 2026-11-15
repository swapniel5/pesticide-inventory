package com.agroshop.product;

import java.net.URI;
import java.util.List;

import com.agroshop.inventory.StockAdjustmentRequest;
import com.agroshop.inventory.StockMovementResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/products")
public class ProductRestController {

    private final ProductService productService;

    public ProductRestController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<ProductResponse> list(@RequestParam(defaultValue = "") String search) {
        return productService.findProducts(search).stream().map(ProductResponse::from).toList();
    }

    @GetMapping("/low-stock")
    public List<ProductResponse> lowStock() {
        return productService.findLowStockProducts().stream().map(ProductResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable Long id) {
        return ProductResponse.from(productService.getProduct(id));
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductForm form) {
        Product product = productService.createProduct(form);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(product.getId())
                .toUri();
        return ResponseEntity.created(location).body(ProductResponse.from(product));
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductForm form) {
        return ProductResponse.from(productService.updateProduct(id, form));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        productService.deactivateProduct(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/stock-movements")
    public ProductResponse recordStockMovement(@PathVariable Long id,
                                               @Valid @RequestBody StockAdjustmentRequest request) {
        return ProductResponse.from(productService.recordStockMovement(
                id, request.movementType(), request.quantity(), request.remarks()));
    }

    @GetMapping("/{id}/stock-movements")
    public List<StockMovementResponse> stockMovements(@PathVariable Long id) {
        return productService.getStockMovements(id).stream().map(StockMovementResponse::from).toList();
    }
}
