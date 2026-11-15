package com.agroshop.customer;

import java.util.List;

import com.agroshop.payment.PaymentService;
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
@RequestMapping("/customers")
public class CustomerController {

    private final CustomerService customerService;
    private final PaymentService paymentService;

    public CustomerController(CustomerService customerService, PaymentService paymentService) {
        this.customerService = customerService;
        this.paymentService = paymentService;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "") String search, Model model) {
        List<CustomerResponse> customers = customerService.searchCustomers(search).stream()
                .map(CustomerResponse::from)
                .toList();
        model.addAttribute("customers", customers);
        model.addAttribute("search", search);
        return "customers/list";
    }

    @GetMapping("/new")
    public String newCustomer(Model model) {
        model.addAttribute("customerForm", new CustomerForm());
        model.addAttribute("formTitle", "Add customer");
        model.addAttribute("formAction", "/customers");
        return "customers/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("customerForm") CustomerForm form,
                         BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepareForm(model, "Add customer", "/customers");
            return "customers/form";
        }
        Customer customer = customerService.createCustomer(form);
        redirectAttributes.addFlashAttribute("successMessage", "Customer added successfully.");
        return "redirect:/customers/" + customer.getId();
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        CustomerProfileResponse profile = customerService.getCustomerProfile(id);
        model.addAttribute("customer", profile.customer());
        model.addAttribute("purchaseHistory", profile.purchaseHistory());
        model.addAttribute("financialSummary", profile.financialSummary());
        model.addAttribute("paymentHistory", paymentService.findCustomerPayments(id));
        return "customers/detail";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("customerForm", CustomerForm.from(customerService.getCustomer(id)));
        model.addAttribute("formTitle", "Edit customer");
        model.addAttribute("formAction", "/customers/" + id + "/edit");
        return "customers/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("customerForm") CustomerForm form,
                         BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepareForm(model, "Edit customer", "/customers/" + id + "/edit");
            return "customers/form";
        }
        customerService.updateCustomer(id, form);
        redirectAttributes.addFlashAttribute("successMessage", "Customer updated successfully.");
        return "redirect:/customers/" + id;
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        customerService.deactivateCustomer(id);
        redirectAttributes.addFlashAttribute("successMessage", "Customer deactivated.");
        return "redirect:/customers/" + id;
    }

    private void prepareForm(Model model, String title, String action) {
        model.addAttribute("formTitle", title);
        model.addAttribute("formAction", action);
    }
}
