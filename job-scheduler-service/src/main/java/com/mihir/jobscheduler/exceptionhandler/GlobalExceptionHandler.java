package com.mihir.jobscheduler.exceptionhandler;

import com.mihir.jobscheduler.dto.ErrorResponse;
import com.mihir.jobscheduler.dto.JobResponse;
import com.mihir.jobscheduler.exception.AuthConflictException;
import com.mihir.jobscheduler.exception.AuthInvalidCredentialsException;
import com.mihir.jobscheduler.exception.JobConflictException;
import com.mihir.jobscheduler.exception.JobDuplicateConflictException;
import com.mihir.jobscheduler.exception.JobNotFoundException;
import com.mihir.jobscheduler.exception.JobValidationException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = new ArrayList<>();
        details.addAll(ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .toList());
        details.addAll(ex.getBindingResult()
                .getGlobalErrors()
                .stream()
                .map(error -> error.getDefaultMessage())
                .toList());

        return build(HttpStatus.BAD_REQUEST, details);
    }

    @ExceptionHandler({JobValidationException.class, ConstraintViolationException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, List.of(ex.getMessage()));
    }

    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(JobNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, List.of(ex.getMessage()));
    }

    @ExceptionHandler(JobConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(JobConflictException ex) {
        return build(HttpStatus.CONFLICT, List.of(ex.getMessage()));
    }

    @ExceptionHandler(AuthConflictException.class)
    public ResponseEntity<ErrorResponse> handleAuthConflict(AuthConflictException ex) {
        return build(HttpStatus.CONFLICT, List.of(ex.getMessage()));
    }

    @ExceptionHandler(AuthInvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(AuthInvalidCredentialsException ex) {
        return build(HttpStatus.UNAUTHORIZED, List.of(ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, List.of(ex.getMessage()));
    }

    @ExceptionHandler(JobDuplicateConflictException.class)
    public ResponseEntity<JobResponse> handleDuplicateConflict(JobDuplicateConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getJobResponse());
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, List<String> details) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(
                        Instant.now(),
                        status.value(),
                        status.getReasonPhrase(),
                        details
                ));
    }
}
