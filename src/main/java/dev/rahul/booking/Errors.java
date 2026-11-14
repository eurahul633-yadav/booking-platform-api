package dev.rahul.booking;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.LoggerFactory;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class Errors extends ResponseEntityExceptionHandler {
  static ProblemDetail problem(HttpStatusCode status, String code, String message) {
    var p = ProblemDetail.forStatusAndDetail(status, message);
    p.setProperty("code", code);
    return p;
  }

  @ExceptionHandler(ApiFailure.class)
  ResponseEntity<ProblemDetail> api(ApiFailure e) {
    return ResponseEntity.status(e.status).body(problem(e.status, e.code, e.getMessage()));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ProblemDetail> conflict(DataIntegrityViolationException e) {
    return ResponseEntity.status(409)
        .body(
            problem(
                HttpStatus.CONFLICT,
                "CONFLICT",
                "The request conflicts with existing data or another booking"));
  }

  @ExceptionHandler({CannotAcquireLockException.class, QueryTimeoutException.class})
  ResponseEntity<ProblemDetail> busy(Exception e) {
    return ResponseEntity.status(503)
        .header("Retry-After", "2")
        .body(
            problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "RETRY_LATER",
                "The operation is busy; retry using the same idempotency key"));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception e, HttpServletRequest request) {
    var id = java.util.UUID.randomUUID().toString();
    LoggerFactory.getLogger(Errors.class).error("Unexpected error reference={}", id, e);
    var p = problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error");
    p.setProperty("reference", id);
    return ResponseEntity.status(500).body(p);
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException e, HttpHeaders h, HttpStatusCode s, WebRequest r) {
    var p = problem(s, "VALIDATION_FAILED", "Check the request fields");
    p.setProperty(
        "errors",
        e.getBindingResult().getFieldErrors().stream()
            .map(
                f ->
                    java.util.Map.of(
                        "field", f.getField(), "message", String.valueOf(f.getDefaultMessage())))
            .toList());
    return new ResponseEntity<>(p, h, s);
  }
}
