package com.poscloud.wallet.auth;

import com.poscloud.wallet.common.*;
import com.poscloud.wallet.common.Types.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccessService {
  private final UserRepository users;

  public User current() {
    var a = SecurityContextHolder.getContext().getAuthentication();
    if (a == null || !a.isAuthenticated()) throw new ApiException("UNAUTHORIZED");
    try {
      var u =
          users
              .findById(UUID.fromString(a.getName()))
              .orElseThrow(() -> new ApiException("UNAUTHORIZED"));
      ApiException.require(u.getStatus() == UserStatus.ACTIVE, "UNAUTHORIZED");
      return u;
    } catch (IllegalArgumentException e) {
      throw new ApiException("UNAUTHORIZED");
    }
  }

  public boolean staff() {
    var r = current().getRole();
    return r == Role.SUPER_ADMIN || r == Role.OPERATIONS;
  }

  public void requireStaff() {
    ApiException.require(staff(), "FORBIDDEN");
  }

  public void customer(UUID id) {
    ApiException.require(
        staff() || id != null && id.equals(current().getCustomerId()), "FORBIDDEN");
  }
}
