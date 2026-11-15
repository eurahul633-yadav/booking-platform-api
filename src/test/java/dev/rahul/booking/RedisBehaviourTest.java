package dev.rahul.booking;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.*;

class RedisBehaviourTest {
  @Test
  void rateLimitIsSharedAtomicAndReturnsRetryAfter() throws Exception {
    var redis = mock(StringRedisTemplate.class);
    when(redis.execute(any(RedisScript.class), anyList())).thenReturn(16L);
    var filter = new RateLimitFilter(redis, true);
    var req = new MockHttpServletRequest("POST", "/api/v1/auth/token");
    req.setRemoteAddr("192.0.2.10");
    var res = new MockHttpServletResponse();
    var chain = new MockFilterChain();
    filter.doFilter(req, res, chain);
    assertThat(res.getStatus()).isEqualTo(429);
    assertThat(res.getHeader("Retry-After")).isEqualTo("60");
    assertThat(chain.getRequest()).isNull();
    verify(redis)
        .execute(
            any(RedisScript.class),
            argThat(
                keys ->
                    keys.size() == 1
                        && keys.getFirst().toString().startsWith("booking:rate:auth:")));
  }

  @Test
  void redisFailureFailsClosedForRateLimit() throws Exception {
    var redis = mock(StringRedisTemplate.class);
    when(redis.execute(any(RedisScript.class), anyList()))
        .thenThrow(new IllegalStateException("offline"));
    var filter = new RateLimitFilter(redis, true);
    var res = new MockHttpServletResponse();
    var chain = new MockFilterChain();
    filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/bookings"), res, chain);
    assertThat(res.getStatus()).isEqualTo(503);
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  void serviceCacheKeysIncludeTenantAndPagination() throws Exception {
    var redis = mock(StringRedisTemplate.class);
    var values = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(values);
    var tenant = UUID.randomUUID();
    var actor = new Actor(UUID.randomUUID(), tenant, "CUSTOMER", UUID.randomUUID());
    var other = new Actor(UUID.randomUUID(), UUID.randomUUID(), "CUSTOMER", UUID.randomUUID());
    String key = "booking:catalog:v3:" + tenant + ":0:20";
    when(values.get(key))
        .thenReturn(
            "[{\"id\":\""
                + UUID.randomUUID()
                + "\",\"name\":\"Lesson\",\"durationMinutes\":30,\"cancellationHours\":24}]");
    String otherKey = "booking:catalog:v3:" + other.tenantId() + ":1:10";
    when(values.get(otherKey)).thenReturn("[]");
    var db = mock(JdbcClient.class);
    var catalog = new CatalogService(db, redis, new ObjectMapper(), true, new BusinessService(db));
    assertThat(catalog.services(actor, 0, 20)).hasSize(1);
    assertThat(catalog.services(other, 1, 10)).isEmpty();
    verifyNoInteractions(db);
    verify(values).get(key);
    verify(values).get(otherKey);
  }

  @Test
  void cacheFailureFallsBackToScopedDatabaseRead() {
    var redis = mock(StringRedisTemplate.class);
    when(redis.opsForValue()).thenThrow(new IllegalStateException("offline"));
    var db = mock(JdbcClient.class, RETURNS_DEEP_STUBS);
    var actor = new Actor(UUID.randomUUID(), UUID.randomUUID(), "CUSTOMER", UUID.randomUUID());
    // A reachable database is requested after the cache fails; no cache exception leaks.
    var catalog = new CatalogService(db, redis, new ObjectMapper(), true, new BusinessService(db));
    assertThat(catalog.services(actor, 0, 20)).isEmpty();
    verify(db).sql(contains("WHERE tenant_id=:tenant"));
  }
}
