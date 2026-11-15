package com.agroshop.inventory;

import java.util.List;

import com.agroshop.product.ProductResponse;
import com.agroshop.product.ProductService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class InventoryController {

    private final ProductService productService;

    public InventoryController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/inventory")
    public String inventory(Model model) {
        prepareInventoryModel(model);
        return "inventory/index";
    }

    @PostMapping("/inventory/stock-movements")
    public String recordMovement(@Valid @ModelAttribute("stockAdjustmentForm") StockAdjustmentForm form,
                                 BindingResult bindingResult, Model model,
                                 RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                productService.recordStockMovement(
                        form.getProductId(), form.getMovementType(), form.getQuantity(), form.getRemarks());
            } catch (InsufficientStockException | IllegalArgumentException exception) {
                bindingResult.reject("stock.adjustment.invalid", exception.getMessage());
            }
        }

        if (bindingResult.hasErrors()) {
            prepareInventoryModel(model);
            return "inventory/index";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Stock movement recorded successfully.");
        return "redirect:/inventory";
    }

    private void prepareInventoryModel(Model model) {
        List<ProductResponse> products = productService.findActiveProducts().stream()
                .map(ProductResponse::from)
                .toList();
        model.addAttribute("products", products);
        model.addAttribute("lowStockProducts", productService.findLowStockProducts().stream()
                .map(ProductResponse::from)
                .toList());
        model.addAttribute("movements", productService.getRecentStockMovements().stream()
                .map(StockMovementResponse::from)
                .toList());
        model.addAttribute("movementTypes", List.of(
                MovementType.PURCHASE, MovementType.ADJUSTMENT_IN, MovementType.ADJUSTMENT_OUT));
        if (!model.containsAttribute("stockAdjustmentForm")) {
            model.addAttribute("stockAdjustmentForm", new StockAdjustmentForm());
        }
    }
}
