package com.agroshop.sales;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.agroshop.customer.CustomerResponse;
import com.agroshop.customer.CustomerService;
import com.agroshop.inventory.InsufficientStockException;
import com.agroshop.product.ProductResponse;
import com.agroshop.product.ProductService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/sales")
public class SaleController {

    private final SaleService saleService;
    private final CustomerService customerService;
    private final ProductService productService;

    public SaleController(
            SaleService saleService,
            CustomerService customerService,
            ProductService productService) {
        this.saleService = saleService;
        this.customerService = customerService;
        this.productService = productService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("sales", saleService.findSales());
        return "sales/list";
    }

    @GetMapping("/new")
    public String newSale(Model model) {
        SaleRequest saleRequest = new SaleRequest();
        saleRequest.setRequestKey(UUID.randomUUID().toString());
        saleRequest.setItems(new ArrayList<>(List.of(new SaleItemRequest())));
        model.addAttribute("saleRequest", saleRequest);
        prepareFormModel(model);
        return "sales/form";
    }

    @PostMapping
    public String create(
            @Valid @ModelAttribute("saleRequest") SaleRequest saleRequest,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepareFormModel(model);
            return "sales/form";
        }
        try {
            SaleResponse sale = saleService.createSale(saleRequest).sale();
            redirectAttributes.addFlashAttribute("successMessage", "Sale completed successfully.");
            return "redirect:/sales/" + sale.id();
        } catch (SaleValidationException | InsufficientStockException exception) {
            bindingResult.reject("sale.invalid", exception.getMessage());
            prepareFormModel(model);
            return "sales/form";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("sale", saleService.getSale(id));
        return "sales/detail";
    }

    private void prepareFormModel(Model model) {
        List<CustomerResponse> customers = customerService.findActiveCustomers().stream()
                .map(CustomerResponse::from)
                .toList();
        List<ProductResponse> products = productService.findActiveProducts().stream()
                .map(ProductResponse::from)
                .toList();
        model.addAttribute("customers", customers);
        model.addAttribute("products", products);
    }
}
