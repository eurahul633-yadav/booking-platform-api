package dev.rahul.booking;

import org.springframework.http.HttpStatus;

public class ApiFailure extends RuntimeException {
  final HttpStatus status;
  final String code;

  public ApiFailure(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public static ApiFailure notFound() {
    return new ApiFailure(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
  }

  public static ApiFailure forbidden() {
    return new ApiFailure(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
  }

  public static ApiFailure conflict(String message) {
    return new ApiFailure(HttpStatus.CONFLICT, "CONFLICT", message);
  }

  public static ApiFailure invalid(String message) {
    return new ApiFailure(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
  }

  public static ApiFailure unauthorized() {
    return new ApiFailure(
        HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required or credentials invalid");
  }
}
