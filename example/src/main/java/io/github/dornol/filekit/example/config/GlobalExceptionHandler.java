package io.github.dornol.filekit.example.config;

import io.github.dornol.filekit.storage.FileStorageException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(ConstraintViolationException ex) {
        List<String> errors = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "error");
        body.put("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<Map<String, Object>> handleFileStorage(FileStorageException ex) {
        HttpStatus status = statusFor(ex.getMessageKey());
        boolean serverError = status.value() >= 500;
        if (serverError) {
            log.error("File storage error [{}]", ex.getMessageKey(), ex);
        } else {
            log.warn("File request rejected [{}]", ex.getMessageKey());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "error");
        body.put("messageKey", ex.getMessageKey());
        body.put("message", serverError
                ? "File operation failed"
                : "File request is invalid");
        return ResponseEntity.status(status).body(body);
    }

    private static HttpStatus statusFor(String messageKey) {
        return switch (messageKey) {
            case FileStorageException.FILE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FileStorageException.FILE_TOO_LARGE,
                 FileStorageException.QUOTA_EXCEEDED -> HttpStatus.CONTENT_TOO_LARGE;
            case FileStorageException.INVALID_FILENAME,
                 FileStorageException.RANGE_NOT_SATISFIABLE,
                 FileStorageException.PRESIGNED_URL_FAILED -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
