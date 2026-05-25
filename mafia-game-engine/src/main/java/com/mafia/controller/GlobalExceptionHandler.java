package com.mafia.controller;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> handleBadRequest(RuntimeException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("status", "error", "message", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleInternal(Exception e) {
        return ResponseEntity.internalServerError()
                .body(Map.of("status", "error", "message", "Internal server error"));
    }
}