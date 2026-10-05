package com.fcv.citas.adapter.in.web;

import com.fcv.citas.application.AppointmentService;
import com.fcv.citas.application.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(AppointmentService.DomainException.class)
    public ResponseEntity<ApiError> appointmentError(AppointmentService.DomainException exception) {
        return ResponseEntity.status(exception.status())
                .body(new ApiError(exception.status(), exception.getMessage()));
    }

    @ExceptionHandler(AuthService.EmailAlreadyUsedException.class)
    public ResponseEntity<ApiError> emailAlreadyUsed() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(409, "Email already registered"));
    }

    @ExceptionHandler(AuthService.InvalidCredentialsException.class)
    public ResponseEntity<ApiError> invalidCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError(401, "Invalid email or password"));
    }

    @ExceptionHandler(AuthService.InvalidRegistrationException.class)
    public ResponseEntity<ApiError> invalidRegistration() {
        return ResponseEntity.badRequest()
                .body(new ApiError(400, "Password must not exceed 72 UTF-8 bytes"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("Request validation failed");
        return ResponseEntity.badRequest().body(new ApiError(400, message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadableBody() {
        return ResponseEntity.badRequest().body(new ApiError(400, "Request body is invalid"));
    }

    public record ApiError(int status, String message) {
    }
}
