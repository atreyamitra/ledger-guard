package com.atreyamitra.ledgerguard.api;

import com.atreyamitra.ledgerguard.domain.BalanceOverflowException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.Map;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, String>> api(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(Map.of("error", ex.getMessage()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
                       MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, String>> invalid(Exception ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid request"));
    }
    @ExceptionHandler(BalanceOverflowException.class)
    ResponseEntity<Map<String, String>> overflow(BalanceOverflowException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "Balance exceeds supported minor-unit range"));
    }
}
