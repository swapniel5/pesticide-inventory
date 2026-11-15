package com.agroshop.sales;

import java.util.Map;

import com.agroshop.inventory.InsufficientStockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = SaleRestController.class)
public class SaleRestExceptionHandler {

    @ExceptionHandler(SaleNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(SaleNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler({SaleValidationException.class, InsufficientStockException.class})
    public ResponseEntity<Map<String, String>> handleInvalidSale(RuntimeException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleInvalidRequest(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .findFirst()
                .orElse("Sale details are invalid");
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}
