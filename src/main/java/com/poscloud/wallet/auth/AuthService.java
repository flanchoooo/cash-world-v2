package com.poscloud.wallet.auth;

import com.poscloud.wallet.audit.AuditService;
import com.poscloud.wallet.common.*;
import com.poscloud.wallet.common.Types.*;
import com.poscloud.wallet.customer.CustomerRepository;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
  private static final long ACCESS_TOKEN_LIFETIME_SECONDS = 86_400;
  private final UserRepository users;
  private final jakarta.persistence.EntityManager entityManager;
  private final RefreshTokenRepository refreshTokens;
  private final CustomerRepository customers;
  private final PasswordEncoder passwords;
  private final JwtEncoder encoder;
  private final AccessService access;
  private final AuditService audit;
  private final JdbcTemplate jdbc;

  @Value("${wallet.issuer}")
  private String issuer;

  @io.swagger.v3.oas.annotations.media.Schema(name = "AuthServiceRegister")
  public record Register(
      @NotBlank @Size(max = 100) String username,
      @NotBlank @Size(min = 8, max = 72) String password,
      UUID customerId,
      @NotNull Role role,
      @Size(max = 40) String mobileNumber,
      @Email @Size(max = 254) String email,
      @Pattern(regexp = "\\d{4}") String mobilePin,
      List<Permission> permissions) {
    public Register(String username, String password, UUID customerId, Role role,
        String mobileNumber, String email, String mobilePin) {
      this(username, password, customerId, role, mobileNumber, email, mobilePin, null);
    }
  }

  @io.swagger.v3.oas.annotations.media.Schema(name = "AuthServiceLogin")
  public record Login(@NotBlank String username, @NotBlank String password) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "AuthServiceRefresh")
  public record Refresh(@NotBlank String refreshToken) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "AuthServiceUserView")
  public record UserView(
      UUID id,
      UUID customerId,
      String username,
      Role role,
      UserStatus status,
      boolean mobilePinConfigured,
      boolean permissionsCustomized,
      List<Permission> permissions) {}

  public record PermissionOption(Permission code, String name, String area) {}

  private UserView userView(User u) {
    var permissions = access.permissions(u.getId());
    if (!u.isPermissionsCustomized() && u.getRole() == Role.OPERATIONS)
      permissions = Arrays.stream(Permission.values())
          .filter(permission -> permission != Permission.USERS_MANAGE)
          .toList();
    return new UserView(
        u.getId(), u.getCustomerId(), u.getUsername(), u.getRole(), u.getStatus(),
        u.getMobilePinHash() != null, u.isPermissionsCustomized(), permissions);
  }

  @io.swagger.v3.oas.annotations.media.Schema(name = "AuthServiceTokens")
  public record Tokens(String accessToken, String refreshToken, long expiresIn, UserView user) {}

  @Transactional
  public UserView register(Register r) {
    ApiException.require(
        r.password().getBytes(StandardCharsets.UTF_8).length <= 72, "INVALID_PASSWORD");
    var actor = access.current();
    boolean admin = actor.getRole() == Role.SUPER_ADMIN;
    boolean corp =
        actor.getRole() == Role.CORPORATE_ADMIN
            && r.customerId() != null
            && r.customerId().equals(actor.getCustomerId())
            && r.role() == Role.CORPORATE_USER;
    ApiException.require(admin || corp, "FORBIDDEN");
    if (r.customerId() != null) {
      var c =
          customers
              .findById(r.customerId())
              .orElseThrow(() -> new ApiException("CUSTOMER_NOT_FOUND"));
      if (r.role() == Role.CORPORATE_ADMIN || r.role() == Role.CORPORATE_USER)
        ApiException.require(
            c.getCustomerType() == CustomerType.CORPORATE, "INVALID_CUSTOMER_TYPE");
      if (r.role() == Role.AGENT) ApiException.require(c.isAgent(), "CUSTOMER_NOT_AGENT");
    } else
      ApiException.require(
          r.role() == Role.SUPER_ADMIN || r.role() == Role.OPERATIONS, "CUSTOMER_REQUIRED");
    var u = new User();
    u.setUsername(r.username());
    u.setPasswordHash(passwords.encode(r.password()));
    u.setCustomerId(r.customerId());
    u.setRole(r.role());
    u.setStatus(UserStatus.ACTIVE);
    u.setEmail(r.email());
    u.setMobileNumber(r.mobileNumber());
    if (r.mobilePin() != null) u.setMobilePinHash(passwords.encode(r.mobilePin()));
    users.save(u);
    if (r.role() == Role.OPERATIONS && r.permissions() != null) {
      for (var permission : new LinkedHashSet<>(r.permissions()))
        jdbc.update(
            "insert into user_permissions (user_id,permission_code,created_at) values (?,?,?)",
            u.getId().toString(), permission.name(), java.sql.Timestamp.from(Instant.now()));
      u.setPermissionsCustomized(true);
    }
    audit.record("USER_REGISTERED", "users", u.getId());
    return userView(u);
  }

  @Transactional
  public Tokens login(Login r) {
    var u = users.findByUsername(r.username()).orElseThrow(() -> new ApiException("UNAUTHORIZED"));
    ApiException.require(
        passwords.matches(r.password(), u.getPasswordHash()) && u.getStatus() == UserStatus.ACTIVE,
        "UNAUTHORIZED");
    u.setLastLoginAt(Instant.now());
    return issue(u);
  }

  @Transactional
  public Tokens refresh(Refresh r) {
    var t =
        refreshTokens
            .findByTokenHash(hash(r.refreshToken()))
            .orElseThrow(() -> new ApiException("UNAUTHORIZED"));
    ApiException.require(!t.isRevoked() && t.getExpiresAt().isAfter(Instant.now()), "UNAUTHORIZED");
    var u = users.lockById(t.getUserId()).orElseThrow(() -> new ApiException("UNAUTHORIZED"));
    ApiException.require(u.getStatus() == UserStatus.ACTIVE, "UNAUTHORIZED");
    t.setRevoked(true);
    return issue(u);
  }

  @Transactional
  public Tokens browserLogin(Login request) {
    var result = login(request);
    requireAdministrativeRole(result.user().role());
    return result;
  }

  @Transactional
  public Tokens externalLogin(Login request) {
    var result = login(request);
    ApiException.require(
        result.user().role() == Role.ESB_SERVICE
            || (result.user().customerId() != null
                && (result.user().role() == Role.CUSTOMER || result.user().role() == Role.AGENT)),
        "FORBIDDEN");
    return result;
  }

  public void verifyMobilePin(String mobilePin) {
    ApiException.require(mobilePin != null && mobilePin.matches("\\d{4}"), "INVALID_MOBILE_PIN");
    var user = access.current();
    ApiException.require(user.getMobilePinHash() != null, "MOBILE_PIN_NOT_CONFIGURED");
    ApiException.require(passwords.matches(mobilePin, user.getMobilePinHash()), "INVALID_MOBILE_PIN");
  }

  @Transactional
  public Tokens browserRefresh(Refresh request) {
    var result = refresh(request);
    requireAdministrativeRole(result.user().role());
    return result;
  }

  private void requireAdministrativeRole(Role role) {
    ApiException.require(
        role == Role.SUPER_ADMIN || role == Role.OPERATIONS || role == Role.CORPORATE_ADMIN,
        "FORBIDDEN");
  }

  @Transactional
  public void logout(Refresh request) {
    refreshTokens
        .findByTokenHash(hash(request.refreshToken()))
        .ifPresent(
            token -> {
              if (token.isRevoked()) return;
              token.setRevoked(true);
              var user = users.lockById(token.getUserId()).orElseThrow();
              user.setTokenVersion(user.getTokenVersion() + 1);
            });
  }

  private Tokens issue(User u) {
    var now = Instant.now();
    var claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(u.getId().toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(ACCESS_TOKEN_LIFETIME_SECONDS))
            .claim("kind", "access")
            .claim("ver", u.getTokenVersion())
            .build();
    String token =
        encoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    byte[] bytes = new byte[48];
    new SecureRandom().nextBytes(bytes);
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    var refresh = new RefreshToken();
    refresh.setUserId(u.getId());
    refresh.setTokenHash(hash(raw));
    refresh.setExpiresAt(now.plusSeconds(604800));
    refreshTokens.save(refresh);
    return new Tokens(token, raw, ACCESS_TOKEN_LIFETIME_SECONDS, userView(u));
  }

  public static String hash(String s) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  @Transactional
  public UserView block(UUID id) {
    return updateUser(id, new UpdateUser(null, UserStatus.BLOCKED));
  }

  public record UpdateUser(Role role, @NotNull UserStatus status, List<Permission> permissions) {
    public UpdateUser(Role role, UserStatus status) { this(role, status, null); }
  }

  public record MobilePinRequest(@NotBlank @Pattern(regexp = "\\d{4}") String mobilePin) {}

  @Transactional
  public UserView updateUser(UUID id, UpdateUser r) {
    var actor = access.current();
    ApiException.require(
        actor.getRole() == Role.SUPER_ADMIN || actor.getRole() == Role.CORPORATE_ADMIN,
        "FORBIDDEN");
    ApiException.require(!actor.getId().equals(id), "CANNOT_CHANGE_OWN_ACCESS");
    // Serialize changes to privileged accounts before locking the target.
    var administrators = users.lockAdministrators(Role.SUPER_ADMIN);
    // Refresh managed instances after waiting for locks so authorization and the
    // last-administrator check see concurrent changes, not earlier cached reads.
    administrators.forEach(
        a -> entityManager.refresh(a, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE));
    entityManager.refresh(actor, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    ApiException.require(
        actor.getStatus() == UserStatus.ACTIVE
            && (actor.getRole() == Role.SUPER_ADMIN || actor.getRole() == Role.CORPORATE_ADMIN),
        "FORBIDDEN");
    var u = users.lockById(id).orElseThrow(() -> new ApiException("USER_NOT_FOUND"));
    var role = r.role() == null ? u.getRole() : r.role();
    if (actor.getRole() == Role.CORPORATE_ADMIN) {
      ApiException.require(
          actor.getCustomerId() != null
              && actor.getCustomerId().equals(u.getCustomerId())
              && u.getRole() == Role.CORPORATE_USER
              && role == Role.CORPORATE_USER,
          "FORBIDDEN");
    }
    if (u.getRole() == Role.SUPER_ADMIN
        && u.getStatus() == UserStatus.ACTIVE
        && (role != Role.SUPER_ADMIN || r.status() != UserStatus.ACTIVE)) {
      ApiException.require(
          administrators.stream().filter(a -> a.getStatus() == UserStatus.ACTIVE).count() > 1,
          "LAST_ADMINISTRATOR");
    }
    if (role != Role.SUPER_ADMIN && role != Role.OPERATIONS) {
      var customer =
          customers
              .findById(java.util.Objects.requireNonNullElse(u.getCustomerId(), new UUID(0, 0)))
              .orElseThrow(() -> new ApiException("CUSTOMER_REQUIRED"));
      if (role == Role.CORPORATE_ADMIN || role == Role.CORPORATE_USER)
        ApiException.require(
            customer.getCustomerType() == CustomerType.CORPORATE, "INVALID_CUSTOMER_TYPE");
      if (role == Role.AGENT) ApiException.require(customer.isAgent(), "CUSTOMER_NOT_AGENT");
    }
    var beforePermissions = access.permissions(id);
    var before = "{\"role\":\"" + u.getRole() + "\",\"status\":\"" + u.getStatus()
        + "\",\"permissionsCustomized\":" + u.isPermissionsCustomized()
        + ",\"permissions\":" + permissionJson(beforePermissions) + "}";
    u.setRole(role);
    u.setStatus(r.status());
    if (role == Role.OPERATIONS && r.permissions() != null) {
      jdbc.update("delete from user_permissions where user_id=?", id.toString());
      for (var permission : new LinkedHashSet<>(r.permissions()))
        jdbc.update(
            "insert into user_permissions (user_id,permission_code,created_at) values (?,?,?)",
            id.toString(), permission.name(), java.sql.Timestamp.from(Instant.now()));
      u.setPermissionsCustomized(true);
    } else if (role != Role.OPERATIONS) {
      jdbc.update("delete from user_permissions where user_id=?", id.toString());
      u.setPermissionsCustomized(false);
    }
    u.setTokenVersion(u.getTokenVersion() + 1);
    audit.record(
        "USER_ACCESS_CHANGED",
        "users",
        id,
        before,
        "{\"role\":\"" + role + "\",\"status\":\"" + r.status()
            + "\",\"permissionsCustomized\":" + u.isPermissionsCustomized()
            + ",\"permissions\":" + permissionJson(access.permissions(id)) + "}");
    return userView(u);
  }

  private String permissionJson(List<Permission> permissions) {
    return permissions.stream().map(permission -> "\"" + permission.name() + "\"")
        .collect(java.util.stream.Collectors.joining(",", "[", "]"));
  }

  @Transactional
  public UserView resetMobilePin(UUID id, MobilePinRequest r) {
    var actor = access.current();
    ApiException.require(
        actor.getRole() == Role.SUPER_ADMIN || actor.getRole() == Role.CORPORATE_ADMIN,
        "FORBIDDEN");
    ApiException.require(!actor.getId().equals(id), "CANNOT_CHANGE_OWN_ACCESS");
    var u = users.lockById(id).orElseThrow(() -> new ApiException("USER_NOT_FOUND"));
    if (actor.getRole() == Role.CORPORATE_ADMIN) {
      ApiException.require(
          actor.getCustomerId() != null
              && actor.getCustomerId().equals(u.getCustomerId())
              && u.getRole() == Role.CORPORATE_USER,
          "FORBIDDEN");
    }
    ApiException.require(
        u.getRole() == Role.CUSTOMER
            || u.getRole() == Role.AGENT
            || u.getRole() == Role.CORPORATE_USER,
        "INVALID_ROLE");
    var before = "{\"mobilePinConfigured\":" + (u.getMobilePinHash() != null) + "}";
    u.setMobilePinHash(passwords.encode(r.mobilePin()));
    u.setTokenVersion(u.getTokenVersion() + 1);
    audit.record(
        "USER_MOBILE_PIN_RESET",
        "users",
        id,
        before,
        "{\"mobilePinConfigured\":true}");
    return userView(u);
  }

  public UserView me() {
    return userView(access.current());
  }

  @Transactional(readOnly = true)
  public UserView getUser(UUID id) {
    var actor = access.current();
    ApiException.require(actor.getRole() == Role.SUPER_ADMIN || actor.getRole() == Role.CORPORATE_ADMIN, "FORBIDDEN");
    var user = users.findById(id).orElseThrow(() -> new ApiException("USER_NOT_FOUND"));
    if (actor.getRole() == Role.CORPORATE_ADMIN)
      ApiException.require(actor.getCustomerId() != null && actor.getCustomerId().equals(user.getCustomerId()) && user.getRole() == Role.CORPORATE_USER, "FORBIDDEN");
    return userView(user);
  }

  public List<PermissionOption> permissionOptions() {
    ApiException.require(access.current().getRole() == Role.SUPER_ADMIN, "FORBIDDEN");
    return List.of(
        new PermissionOption(Permission.OVERVIEW_VIEW, "Overview", "General"),
        new PermissionOption(Permission.CUSTOMERS_VIEW, "View customers", "Customers"),
        new PermissionOption(Permission.CUSTOMERS_MANAGE, "Create and manage customers", "Customers"),
        new PermissionOption(Permission.WALLETS_VIEW, "View wallets", "Wallets"),
        new PermissionOption(Permission.WALLET_MANAGE, "Create and manage wallets", "Wallets"),
        new PermissionOption(Permission.WALLET_DEPOSIT, "Deposit funds", "Wallets"),
        new PermissionOption(Permission.WALLET_WITHDRAW, "Withdraw funds", "Wallets"),
        new PermissionOption(Permission.WALLET_SEND, "Send money", "Wallets"),
        new PermissionOption(Permission.WALLET_ADJUST, "Adjust balances", "Wallets"),
        new PermissionOption(Permission.TRANSACTIONS_VIEW, "View transactions", "Transactions"),
        new PermissionOption(Permission.TRANSACTION_REVERSE, "Reverse transactions", "Transactions"),
        new PermissionOption(Permission.REMITTANCES_VIEW, "View remittances", "Remittances"),
        new PermissionOption(Permission.REMITTANCE_SEND, "Send remittances", "Remittances"),
        new PermissionOption(Permission.REMITTANCE_CASHOUT, "Cash out remittances", "Remittances"),
        new PermissionOption(Permission.REMITTANCE_REPORTS_VIEW, "View remittance reports", "Reports"),
        new PermissionOption(Permission.CREDIT_SALES_VIEW, "View credit sales", "Credit sales"),
        new PermissionOption(Permission.CREDIT_SALES_MANAGE, "Collect credit sales", "Credit sales"),
        new PermissionOption(Permission.COMMISSIONS_VIEW, "View commissions", "Commissions"),
        new PermissionOption(Permission.CONFIGURATION_VIEW, "View configuration", "Administration"),
        new PermissionOption(Permission.CONFIGURATION_MANAGE, "Change configuration", "Administration"),
        new PermissionOption(Permission.AUDIT_VIEW, "View audit trail", "Administration"),
        new PermissionOption(Permission.EXPENSES_MANAGE, "Manage expenses", "Administration"));
  }
}
