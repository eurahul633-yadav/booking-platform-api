package dev.rahul.booking;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
  private final StringRedisTemplate redis;
  private final boolean enabled;
  private static final DefaultRedisScript<Long> SCRIPT =
      new DefaultRedisScript<>(
          "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],60) end;"
              + " return n",
          Long.class);

  public RateLimitFilter(
      StringRedisTemplate redis, @Value("${app.rate-limit-enabled}") boolean enabled) {
    this.redis = redis;
    this.enabled = enabled;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (enabled && request.getRequestURI().startsWith("/api/")) {
      boolean auth =
          request.getRequestURI().startsWith("/api/v1/auth/") && "POST".equals(request.getMethod());
      String key =
          "booking:rate:"
              + (auth ? "auth:" : "api:")
              + SessionService.hash(request.getRemoteAddr());
      try {
        Long count = redis.execute(SCRIPT, List.of(key));
        if (count == null) throw new IllegalStateException("Rate limiter unavailable");
        if (count > (auth ? 15 : 300)) {
          response.setHeader("Retry-After", "60");
          SecurityConfig.writeError(response, 429, "RATE_LIMITED", "Too many requests");
          return;
        }
      } catch (Exception e) {
        SecurityConfig.writeError(
            response, 503, "RATE_LIMIT_UNAVAILABLE", "Request limiting unavailable; retry later");
        return;
      }
    }
    chain.doFilter(request, response);
  }
}
