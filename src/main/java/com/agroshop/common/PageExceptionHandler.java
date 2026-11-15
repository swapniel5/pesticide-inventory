package com.agroshop.common;

import com.agroshop.product.ProductNotFoundException;
import com.agroshop.product.ProductController;
import com.agroshop.inventory.InventoryController;
import com.agroshop.customer.CustomerController;
import com.agroshop.customer.CustomerNotFoundException;
import com.agroshop.sales.SaleController;
import com.agroshop.sales.SaleNotFoundException;
import com.agroshop.payment.PaymentController;
import com.agroshop.payment.PaymentNotFoundException;
import com.agroshop.receipt.ReceiptController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice(assignableTypes = {
        ProductController.class,
        InventoryController.class,
        CustomerController.class,
        SaleController.class,
        PaymentController.class,
        ReceiptController.class
})
public class PageExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ModelAndView handleNotFound(ProductNotFoundException exception) {
        ModelAndView modelAndView = new ModelAndView("errors/not-found");
        modelAndView.addObject("message", exception.getMessage());
        modelAndView.setStatus(HttpStatus.NOT_FOUND);
        return modelAndView;
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    public ModelAndView handleCustomerNotFound(CustomerNotFoundException exception) {
        ModelAndView modelAndView = new ModelAndView("errors/not-found");
        modelAndView.addObject("message", exception.getMessage());
        modelAndView.setStatus(HttpStatus.NOT_FOUND);
        return modelAndView;
    }

    @ExceptionHandler(SaleNotFoundException.class)
    public ModelAndView handleSaleNotFound(SaleNotFoundException exception) {
        ModelAndView modelAndView = new ModelAndView("errors/not-found");
        modelAndView.addObject("message", exception.getMessage());
        modelAndView.setStatus(HttpStatus.NOT_FOUND);
        return modelAndView;
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ModelAndView handlePaymentNotFound(PaymentNotFoundException exception) {
        ModelAndView modelAndView = new ModelAndView("errors/not-found");
        modelAndView.addObject("message", exception.getMessage());
        modelAndView.setStatus(HttpStatus.NOT_FOUND);
        return modelAndView;
    }
}
