package dev.rahul.booking;

import java.util.UUID;

public record Actor(UUID userId, UUID tenantId, String role, UUID sessionId) {
  public boolean admin() {
    return "ADMIN".equals(role);
  }

  public void requireAdmin() {
    if (!admin()) throw ApiFailure.forbidden();
  }
}
