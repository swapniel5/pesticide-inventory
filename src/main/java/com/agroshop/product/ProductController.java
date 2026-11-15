package com.agroshop.product;

import java.util.List;

import com.agroshop.inventory.StockMovementResponse;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "") String search, Model model) {
        List<ProductResponse> products = productService.findProducts(search).stream()
                .map(ProductResponse::from)
                .toList();
        model.addAttribute("products", products);
        model.addAttribute("search", search);
        return "products/list";
    }

    @GetMapping("/new")
    public String newProduct(Model model) {
        model.addAttribute("productForm", new ProductForm());
        model.addAttribute("formTitle", "Add product");
        model.addAttribute("formAction", "/products");
        model.addAttribute("isNew", true);
        return "products/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("productForm") ProductForm form,
                         BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepareForm(model, "Add product", "/products", true);
            return "products/form";
        }
        Product product = productService.createProduct(form);
        redirectAttributes.addFlashAttribute("successMessage", "Product added successfully.");
        return "redirect:/products/" + product.getId();
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Product product = productService.getProduct(id);
        model.addAttribute("product", ProductResponse.from(product));
        model.addAttribute("movements", productService.getStockMovements(id).stream()
                .map(StockMovementResponse::from)
                .toList());
        return "products/detail";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("productForm", ProductForm.from(productService.getProduct(id)));
        model.addAttribute("formTitle", "Edit product");
        model.addAttribute("formAction", "/products/" + id + "/edit");
        model.addAttribute("isNew", false);
        return "products/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("productForm") ProductForm form,
                         BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepareForm(model, "Edit product", "/products/" + id + "/edit", false);
            return "products/form";
        }
        productService.updateProduct(id, form);
        redirectAttributes.addFlashAttribute("successMessage", "Product updated successfully.");
        return "redirect:/products/" + id;
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        productService.deactivateProduct(id);
        redirectAttributes.addFlashAttribute("successMessage", "Product deactivated.");
        return "redirect:/products/" + id;
    }

    private void prepareForm(Model model, String title, String action, boolean isNew) {
        model.addAttribute("formTitle", title);
        model.addAttribute("formAction", action);
        model.addAttribute("isNew", isNew);
    }
}
