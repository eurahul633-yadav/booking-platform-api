package dev.rahul.booking;

import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/invitations")
public class InvitationController {
  private final IdentityService identity;

  public InvitationController(IdentityService identity) {
    this.identity = identity;
  }

  @PostMapping("/accept")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, UUID> accept(@Valid @RequestBody PlatformModels.AcceptInvite request) {
    return Map.of("userId", identity.accept(request));
  }
}
