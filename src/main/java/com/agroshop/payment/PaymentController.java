package com.agroshop.payment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.agroshop.customer.CustomerNotFoundException;
import com.agroshop.customer.CustomerResponse;
import com.agroshop.customer.CustomerService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PaymentController {

    private final PaymentService paymentService;
    private final CustomerService customerService;

    public PaymentController(PaymentService paymentService, CustomerService customerService) {
        this.paymentService = paymentService;
        this.customerService = customerService;
    }

    @GetMapping("/payments")
    public String list(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Model model) {
        try {
            model.addAttribute("payments", paymentService.findPayments(customerId, fromDate, toDate));
        } catch (PaymentValidationException exception) {
            model.addAttribute("payments", List.of());
            model.addAttribute("filterError", exception.getMessage());
        }
        model.addAttribute("customers", customerService.searchCustomers(""));
        model.addAttribute("customerId", customerId);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        return "payments/list";
    }

    @GetMapping("/payments/new")
    public String selectCustomerForPayment(
            @RequestParam(required = false) Long customerId,
            Model model) {
        if (customerId != null) {
            return "redirect:/customers/" + customerId + "/payments/new";
        }
        model.addAttribute("customers", customerService.findActiveCustomers().stream()
                .map(CustomerResponse::from)
                .toList());
        return "payments/select-customer";
    }

    @GetMapping("/customers/{customerId}/payments/new")
    public String newPayment(@PathVariable Long customerId, Model model) {
        PaymentForm form = new PaymentForm();
        form.setCustomerId(customerId);
        form.setPaymentDate(LocalDateTime.now().withSecond(0).withNano(0));
        form.setRequestKey(UUID.randomUUID().toString());
        prepareFormModel(form, model);
        model.addAttribute("paymentForm", form);
        return "payments/form";
    }

    @PostMapping("/payments")
    public String create(
            @Valid @ModelAttribute("paymentForm") PaymentForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                PaymentView payment = paymentService.recordPayment(form);
                redirectAttributes.addFlashAttribute("successMessage", "Payment recorded successfully.");
                return "redirect:/payments/" + payment.id();
            } catch (PaymentValidationException exception) {
                bindingResult.reject("payment.invalid", exception.getMessage());
            } catch (CustomerNotFoundException exception) {
                bindingResult.reject("payment.invalid", "Select an existing customer.");
            }
        }
        prepareFormModel(form, model);
        return "payments/form";
    }

    @GetMapping("/payments/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("payment", paymentService.getPayment(id));
        return "payments/detail";
    }

    private void prepareFormModel(PaymentForm form, Model model) {
        List<CustomerResponse> customers = customerService.searchCustomers("").stream()
                .map(CustomerResponse::from)
                .toList();
        List<OutstandingInvoice> invoices = List.of();
        if (form.getCustomerId() != null) {
            try {
                invoices = paymentService.findOutstandingInvoices(form.getCustomerId());
            } catch (CustomerNotFoundException exception) {
                model.addAttribute("customerError", "Select an existing customer.");
            }
        }

        Map<Long, PaymentAllocationForm> currentAllocations = new HashMap<>();
        for (PaymentAllocationForm allocation : form.getAllocations()) {
            if (allocation != null && allocation.getSaleId() != null) {
                currentAllocations.put(allocation.getSaleId(), allocation);
            }
        }
        List<PaymentAllocationForm> formAllocations = new ArrayList<>();
        for (OutstandingInvoice invoice : invoices) {
            PaymentAllocationForm allocation = currentAllocations.get(invoice.saleId());
            if (allocation == null) {
                allocation = new PaymentAllocationForm();
                allocation.setSaleId(invoice.saleId());
            }
            formAllocations.add(allocation);
        }
        form.setAllocations(formAllocations);

        model.addAttribute("customers", customers);
        model.addAttribute("outstandingInvoices", invoices);
        model.addAttribute("paymentMethods", PaymentMethod.values());
    }
}
