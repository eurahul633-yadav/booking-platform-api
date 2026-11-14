package dev.rahul.booking;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.*;
import org.springframework.web.cors.*;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {
  @Bean
  FilterRegistrationBean<RateLimitFilter> rateRegistration(RateLimitFilter filter) {
    var registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }

  @Bean
  FilterRegistrationBean<RequestBodyLimitFilter> bodyRegistration(RequestBodyLimitFilter filter) {
    var registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }

  @Bean
  PasswordEncoder passwords() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  CookieCsrfTokenRepository csrfRepository(@Value("${app.cookie-secure}") boolean secure) {
    var csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
    csrf.setCookieCustomizer(c -> c.path("/").secure(secure).sameSite("Strict"));
    return csrf;
  }

  @Bean
  SecurityFilterChain security(
      HttpSecurity http,
      SessionService sessions,
      CookieCsrfTokenRepository csrf,
      RateLimitFilter limiter,
      RequestBodyLimitFilter bodyLimit,
      @Value("${app.cookie-secure}") boolean secure,
      @Value("${app.allowed-origins}") String origins)
      throws Exception {
    var handler = new CsrfTokenRequestAttributeHandler();
    http.sessionManagement(
            s ->
                s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    .sessionAuthenticationStrategy(
                        new org.springframework.security.web.authentication.session
                            .NullAuthenticatedSessionStrategy()))
        .formLogin(f -> f.disable())
        .httpBasic(b -> b.disable())
        .logout(l -> l.disable())
        .requestCache(r -> r.disable())
        .cors(
            c ->
                c.configurationSource(
                    request -> {
                      var config = new CorsConfiguration();
                      config.setAllowedOrigins(
                          Arrays.stream(origins.split(",")).map(String::trim).toList());
                      config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                      config.setAllowedHeaders(
                          List.of(
                              "Content-Type", "Authorization", "X-XSRF-TOKEN", "Idempotency-Key"));
                      config.setAllowCredentials(true);
                      config.setMaxAge(600L);
                      return config;
                    }))
        .csrf(
            c ->
                c.csrfTokenRepository(csrf)
                    .csrfTokenRequestHandler(handler)
                    .ignoringRequestMatchers(
                        request ->
                            request.getHeader("Authorization") != null
                                && request.getHeader("Authorization").startsWith("Bearer ")))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/api/v1/auth/csrf",
                        "/api/v1/auth/workspaces",
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/token",
                        "/api/v1/auth/invitations/accept",
                        "/actuator/health",
                        "/actuator/health/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, ex) ->
                            writeError(res, 401, "UNAUTHORIZED", "Authentication required"))
                    .accessDeniedHandler(
                        (req, res, ex) ->
                            writeError(
                                res,
                                403,
                                "FORBIDDEN",
                                "Access denied or CSRF token missing/invalid")))
        .addFilterBefore(limiter, CsrfFilter.class)
        .addFilterBefore(bodyLimit, CsrfFilter.class)
        .addFilterBefore(
            new OncePerRequestFilter() {
              @Override
              protected void doFilterInternal(
                  HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                  throws ServletException, IOException {
                String header = req.getHeader("Authorization");
                String token = null;
                String kind = "COOKIE";
                if (header != null) {
                  if (!header.startsWith("Bearer ")) {
                    writeError(res, 401, "UNAUTHORIZED", "Invalid authorization scheme");
                    return;
                  }
                  token = header.substring(7);
                  kind = "BEARER";
                } else if (req.getCookies() != null) {
                  for (var cookie : req.getCookies())
                    if ("BOOKING_SESSION".equals(cookie.getName())) token = cookie.getValue();
                }
                // Public credential/bootstrap routes must remain reachable with a stale browser
                // cookie.
                if ("COOKIE".equals(kind)
                    && java.util.Set.of(
                            "/api/v1/auth/csrf",
                            "/api/v1/auth/workspaces",
                            "/api/v1/auth/register",
                            "/api/v1/auth/login",
                            "/api/v1/auth/token",
                            "/api/v1/auth/invitations/accept")
                        .contains(req.getRequestURI())) token = null;
                if (token != null) {
                  java.util.Optional<Actor> actor;
                  try {
                    actor = sessions.authenticate(token, kind);
                  } catch (org.springframework.dao.DataAccessException e) {
                    writeError(
                        res,
                        503,
                        "AUTH_UNAVAILABLE",
                        "Authentication service unavailable; retry later");
                    return;
                  }
                  if (actor.isEmpty()) {
                    writeError(res, 401, "UNAUTHORIZED", "Session expired or token invalid");
                    return;
                  }
                  var auth =
                      new UsernamePasswordAuthenticationToken(
                          actor.get(),
                          null,
                          List.of(new SimpleGrantedAuthority("ROLE_" + actor.get().role())));
                  SecurityContextHolder.getContext().setAuthentication(auth);
                }
                chain.doFilter(req, res);
              }
            },
            AnonymousAuthenticationFilter.class);
    return http.build();
  }

  static void writeError(HttpServletResponse response, int status, String code, String detail)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/problem+json");
    response
        .getWriter()
        .write(
            "{\"type\":\"about:blank\",\"status\":"
                + status
                + ",\"code\":\""
                + code
                + "\",\"detail\":\""
                + detail
                + "\"}");
  }
}
