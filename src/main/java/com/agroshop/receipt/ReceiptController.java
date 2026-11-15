package com.agroshop.receipt;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class ReceiptController {

    private final ReceiptService receiptService;

    public ReceiptController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    @GetMapping("/sales/{id}/receipt")
    public String receipt(@PathVariable Long id, Model model) {
        model.addAttribute("receipt", receiptService.getReceipt(id));
        return "receipts/receipt";
    }
}
