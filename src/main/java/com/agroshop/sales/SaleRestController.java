package com.agroshop.sales;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/sales")
public class SaleRestController {

    private final SaleService saleService;

    public SaleRestController(SaleService saleService) {
        this.saleService = saleService;
    }

    @GetMapping
    public List<SaleResponse> list() {
        return saleService.findSales();
    }

    @GetMapping("/{id}")
    public SaleResponse get(@PathVariable Long id) {
        return saleService.getSale(id);
    }

    @PostMapping
    public ResponseEntity<SaleResponse> create(@Valid @RequestBody SaleRequest request) {
        SaleCreationResult result = saleService.createSale(request);
        if (!result.created()) {
            return ResponseEntity.ok(result.sale());
        }
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(result.sale().id())
                .toUri();
        return ResponseEntity.created(location).body(result.sale());
    }
}
