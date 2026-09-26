package com.amex.benefit_activation_engine.controller;

import com.amex.benefit_activation_engine.service.ClaimNotEntitledException;
import com.amex.benefit_activation_engine.service.ClaimAccessDeniedException;
import com.amex.benefit_activation_engine.service.ClaimNotFoundException;
import com.amex.benefit_activation_engine.service.IllegalClaimTransitionException;
import com.amex.benefit_activation_engine.service.PrefillIncompleteException;
import com.amex.benefit_activation_engine.service.TransactionNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates common failures into clean HTTP responses:
 * 400 for validation errors (with per-field messages) and 404 for missing
 * resources.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setTitle("Invalid transaction");

        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    public ProblemDetail handleNotFound(TransactionNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Not found");
        return problem;
    }

    @ExceptionHandler(ClaimNotFoundException.class)
    public ProblemDetail handleClaimNotFound(ClaimNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Not found");
        return problem;
    }

    @ExceptionHandler(PrefillIncompleteException.class)
    public ProblemDetail handlePrefillIncomplete(PrefillIncompleteException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Pre-fill incomplete");
        return problem;
    }

    @ExceptionHandler(IllegalClaimTransitionException.class)
    public ProblemDetail handleIllegalTransition(IllegalClaimTransitionException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Illegal claim transition");
        return problem;
    }

    @ExceptionHandler(ClaimNotEntitledException.class)
    public ProblemDetail handleNotEntitled(ClaimNotEntitledException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Claim not entitled");
        return problem;
    }

    @ExceptionHandler(ClaimAccessDeniedException.class)
    public ProblemDetail handleClaimAccessDenied(ClaimAccessDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setTitle("Forbidden");
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setTitle("Invalid transaction");

        Map<String, String> errors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            errors.put(String.valueOf(violation.getPropertyPath()), violation.getMessage());
        }
        problem.setProperty("errors", errors);
        return problem;
    }
}
