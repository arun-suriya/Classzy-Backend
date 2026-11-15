package com.centram.classzy.common;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** Every error leaves the API as {"message": "...", "errors": {field: message}}. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    public record ApiError(String message, Map<String, String> errors) {
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException e) {
        return ResponseEntity.status(e.status()).body(new ApiError(e.getMessage(), Map.of()));
    }

    @ExceptionHandler(FieldErrorsException.class)
    public ResponseEntity<ApiError> handleFields(FieldErrorsException e) {
        String message = "Please review: " + String.join("; ", e.errors().values()) + ".";
        return ResponseEntity.badRequest().body(new ApiError(message, e.errors()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ApiError("That file is too large. Logos can be up to 2 MB and banners up to 5 MB.", Map.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> errors.putIfAbsent(f.getField(), f.getDefaultMessage()));
        String message = errors.isEmpty()
                ? "Please check the form values."
                : "Please review: " + String.join("; ", errors.entrySet().stream()
                        .map(en -> en.getKey() + " " + en.getValue()).toList()) + ".";
        return ResponseEntity.badRequest().body(new ApiError(message, errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(new ApiError("The request body is missing or malformed.", Map.of()));
    }

    /** Safety net for unique / overlap / foreign-key rules enforced by the database. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(
                "This change conflicts with existing data (duplicate, overlapping, or still in use).", Map.of()));
    }
}
