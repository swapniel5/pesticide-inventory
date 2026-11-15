package com.agroshop.inventory;

import java.util.List;

import com.agroshop.product.ProductResponse;
import com.agroshop.product.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
public class InventoryRestController {

    private final ProductService productService;

    public InventoryRestController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<ProductResponse> currentStock() {
        return productService.findActiveProducts().stream().map(ProductResponse::from).toList();
    }
}
