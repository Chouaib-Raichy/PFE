package ma.fsac.pfeecom.cart.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Gestionnaire centralisé des exceptions pour le Cart Service.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return new ResponseEntity<>(new ErrorResponse(
                Instant.now(), HttpStatus.NOT_FOUND.value(), "NOT_FOUND", ex.getMessage(), null),
                HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err -> errors.put(err.getField(), err.getDefaultMessage()));
        return new ResponseEntity<>(new ErrorResponse(
                Instant.now(), HttpStatus.BAD_REQUEST.value(), "VALIDATION_FAILED",
                "Erreur de validation des données", errors), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        return new ResponseEntity<>(new ErrorResponse(
                Instant.now(), HttpStatus.BAD_REQUEST.value(), "VALIDATION_FAILED", ex.getMessage(), null),
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return new ResponseEntity<>(new ErrorResponse(
                Instant.now(), HttpStatus.BAD_REQUEST.value(), "MALFORMED_JSON",
                "Le corps de la requête est invalide ou malformé.", null), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return new ResponseEntity<>(new ErrorResponse(
                Instant.now(), HttpStatus.FORBIDDEN.value(), "FORBIDDEN",
                ex.getMessage(), null), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        return new ResponseEntity<>(new ErrorResponse(
                Instant.now(), HttpStatus.INTERNAL_SERVER_ERROR.value(), "INTERNAL_ERROR",
                "Une erreur interne est survenue. Veuillez réessayer plus tard.", null),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public record ErrorResponse(
            Instant timestamp,
            int status,
            String code,
            String message,
            Map<String, String> details
    ) {}
}