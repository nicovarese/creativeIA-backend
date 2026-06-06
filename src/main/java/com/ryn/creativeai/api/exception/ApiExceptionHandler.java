package com.ryn.creativeai.api.exception;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;


/**
 * Traductor de excepciones a respuestas HTTP.
 * - IllegalArgumentException -> 400 invalid_parameters
 * - Body JSON inválido -> 400 malformed_json
 * - Validaciones @Valid -> 400 validation_error
 * - Fallas inesperadas -> 500 internal_error
 */
@ControllerAdvice
public class ApiExceptionHandler {

    private ResponseEntity<Object> problem(HttpStatus status, String code, String message, Map<String,Object> extra) {
        Map<String,Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("code", code);
        body.put("message", message);
        if (extra != null && !extra.isEmpty()) body.put("details", extra);
        return ResponseEntity.status(status).body(body);
    }

    /* ===== 400 BAD REQUEST (errores del cliente) ===== */

    // Tu validación de schema/params suele lanzar IllegalArgumentException
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "invalid_parameters", ex.getMessage(), null);
    }

    // JSON mal formado o tipos incorrectos en el body
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleNotReadable(HttpMessageNotReadableException ex) {
        String msg = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        return problem(HttpStatus.BAD_REQUEST, "malformed_json", msg, null);
    }

    // Errores de mapeo (ej: esperaba objeto y vino array)
    @ExceptionHandler(MismatchedInputException.class)
    public ResponseEntity<Object> handleMismatched(MismatchedInputException ex) {
        return problem(HttpStatus.BAD_REQUEST, "type_mismatch", ex.getOriginalMessage(), null);
    }

    // Validaciones Bean Validation (@Valid) en DTOs
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<Object> handleValidation(Exception ex) {
        Map<String,Object> details = new HashMap<>();
        if (ex instanceof MethodArgumentNotValidException manv) {
            manv.getBindingResult().getFieldErrors()
                    .forEach(err -> details.put(err.getField(), err.getDefaultMessage()));
        } else if (ex instanceof BindException be) {
            be.getBindingResult().getFieldErrors()
                    .forEach(err -> details.put(err.getField(), err.getDefaultMessage()));
        }
        return problem(HttpStatus.BAD_REQUEST, "validation_error", "Request validation failed", details);
    }

    // Constraint violations (ej: @Size, @Min en query params/path vars)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraint(ConstraintViolationException ex) {
        Map<String,Object> details = new HashMap<>();
        ex.getConstraintViolations().forEach(v -> details.put(v.getPropertyPath().toString(), v.getMessage()));
        return problem(HttpStatus.BAD_REQUEST, "constraint_violation", "Constraint violation", details);
    }

    // Falta de query param requerido
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Object> handleMissingParam(MissingServletRequestParameterException ex) {
        Map<String,Object> details = Map.of(ex.getParameterName(), "is required");
        return problem(HttpStatus.BAD_REQUEST, "missing_parameter", "Missing request parameter", details);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Object> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        return problem(status, "http_error", message, null);
    }

    /* ===== 500 INTERNAL SERVER ERROR (nuestro bug/falla externa) ===== */

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Object> handleRuntime(RuntimeException ex) {
        // Podés loguearlo con logger; acá simplificamos
        ex.printStackTrace();
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error",
                "Ocurrió un error inesperado. Intenta de nuevo.", null);
    }
}
