package com.poscloud.wallet.auth;

import com.poscloud.wallet.common.*;
import com.poscloud.wallet.common.Types.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccessService {
  private final UserRepository users;
  private final JdbcTemplate jdbc;

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

  public boolean hasPermission(Permission permission) {
    var user = current();
    if (user.getRole() == Role.SUPER_ADMIN) return true;
    if (user.isPermissionsCustomized()) {
      boolean granted = jdbc.queryForObject(
              "select count(*) from user_permissions where user_id=? and permission_code=?",
              Integer.class,
              user.getId().toString(),
              permission.name())
          > 0;
      if (granted) return true;
      if (permission == Permission.WALLETS_VIEW)
        return hasAny(user.getId(), Permission.WALLET_MANAGE, Permission.WALLET_DEPOSIT,
            Permission.WALLET_WITHDRAW, Permission.WALLET_SEND, Permission.WALLET_ADJUST);
      if (permission == Permission.CUSTOMERS_VIEW)
        return hasAny(user.getId(), Permission.CUSTOMERS_MANAGE);
      if (permission == Permission.REMITTANCES_VIEW)
        return hasAny(user.getId(), Permission.REMITTANCE_SEND, Permission.REMITTANCE_CASHOUT);
      if (permission == Permission.CONFIGURATION_VIEW)
        return hasAny(user.getId(), Permission.CONFIGURATION_MANAGE);
      return false;
    }
    if (user.getRole() == Role.OPERATIONS) return true;
    if (user.getRole() == Role.CORPORATE_ADMIN)
      return switch (permission) {
        case OVERVIEW_VIEW, WALLETS_VIEW, REMITTANCES_VIEW, REMITTANCE_SEND, USERS_MANAGE -> true;
        default -> false;
      };
    return false;
  }

  private boolean hasAny(UUID userId, Permission... permissions) {
    for (var permission : permissions)
      if (jdbc.queryForObject(
              "select count(*) from user_permissions where user_id=? and permission_code=?",
              Integer.class,
              userId.toString(),
              permission.name())
          > 0) return true;
    return false;
  }

  public void requirePermission(Permission permission) {
    ApiException.require(hasPermission(permission), "FORBIDDEN");
  }

  public java.util.List<Permission> permissions(UUID userId) {
    return jdbc.query(
        "select permission_code from user_permissions where user_id=? order by permission_code",
        (rs, row) -> Permission.valueOf(rs.getString(1)),
        userId.toString());
  }

  public void customer(UUID id) {
    ApiException.require(
        staff() || id != null && id.equals(current().getCustomerId()), "FORBIDDEN");
  }
}
