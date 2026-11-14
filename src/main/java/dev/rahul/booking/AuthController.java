package dev.rahul.booking;

import static dev.rahul.booking.Models.*;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final SessionService sessions;
  private final boolean secure;
  private final org.springframework.security.web.csrf.CookieCsrfTokenRepository csrfRepository;

  public AuthController(
      SessionService sessions,
      @Value("${app.cookie-secure}") boolean secure,
      org.springframework.security.web.csrf.CookieCsrfTokenRepository csrfRepository) {
    this.sessions = sessions;
    this.secure = secure;
    this.csrfRepository = csrfRepository;
  }

  @GetMapping("/csrf")
  public Map<String, String> csrf(CsrfToken csrf) {
    return Map.of("token", csrf.getToken(), "headerName", csrf.getHeaderName());
  }

  @PostMapping("/workspaces")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, UUID> workspace(@Valid @RequestBody WorkspaceRequest r) {
    return sessions.workspace(r);
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, UUID> register(@Valid @RequestBody RegisterRequest r) {
    return Map.of("userId", sessions.register(r));
  }

  @PostMapping("/token")
  public SessionService.Issued token(
      @Valid @RequestBody LoginRequest r, HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
    return sessions.login(r, "BEARER");
  }

  @PostMapping("/login")
  public Map<String, Long> login(
      @Valid @RequestBody LoginRequest r,
      HttpServletRequest request,
      HttpServletResponse response) {
    var issued = sessions.login(r, "COOKIE");
    cookie(response, issued.accessToken(), issued.expiresIn());
    csrfRepository.saveToken(null, request, response);
    response.setHeader("Cache-Control", "no-store");
    return Map.of("expiresIn", issued.expiresIn());
  }

  @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
  @GetMapping("/me")
  public Actor me(@AuthenticationPrincipal Actor actor, HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
    return actor;
  }

  @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(@AuthenticationPrincipal Actor actor, HttpServletResponse response) {
    sessions.logout(actor);
    cookie(response, "", 0);
  }

  @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
  @PostMapping("/logout-all")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logoutAll(@AuthenticationPrincipal Actor actor, HttpServletResponse response) {
    sessions.logoutAll(actor);
    cookie(response, "", 0);
  }

  private void cookie(HttpServletResponse response, String value, long seconds) {
    response.addHeader(
        "Set-Cookie",
        ResponseCookie.from("BOOKING_SESSION", value)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Strict")
            .path("/")
            .maxAge(Duration.ofSeconds(seconds))
            .build()
            .toString());
  }
}
