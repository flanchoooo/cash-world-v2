package com.poscloud.wallet.config;

import com.poscloud.wallet.auth.User;
import com.poscloud.wallet.auth.UserRepository;
import com.poscloud.wallet.common.Types.Role;
import com.poscloud.wallet.common.Types.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BootstrapUsers implements ApplicationRunner {
  private static final Logger LOG = LoggerFactory.getLogger(BootstrapUsers.class);

  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final JdbcTemplate jdbc;
  private final String adminUsername;
  private final String adminPassword;
  private final String esbUsername;
  private final String esbPassword;

  public BootstrapUsers(
      UserRepository users,
      PasswordEncoder passwords,
      JdbcTemplate jdbc,
      @Value("${wallet.bootstrap.username:}") String adminUsername,
      @Value("${wallet.bootstrap.password:}") String adminPassword,
      @Value("${wallet.esb-service.username:CUS00000001}") String esbUsername,
      @Value("${wallet.esb-service.password:CUS00000001}") String esbPassword) {
    this.users = users;
    this.passwords = passwords;
    this.jdbc = jdbc;
    this.adminUsername = adminUsername;
    this.adminPassword = adminPassword;
    this.esbUsername = esbUsername;
    this.esbPassword = esbPassword;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    seed("admin", adminUsername, adminPassword, Role.SUPER_ADMIN);
    seed("ESB service", esbUsername, esbPassword, Role.ESB_SERVICE);
  }

  private void seed(String label, String username, String password, Role role) {
    if (username == null || username.isBlank()) return;
    if (password == null || password.length() < 8)
      throw new IllegalStateException(label + " bootstrap password must be 8+ characters");

    var existing = users.findByUsername(username);
    if (existing.isEmpty()) {
      var user = new User();
      user.setUsername(username);
      user.setPasswordHash(passwords.encode(password));
      user.setRole(role);
      user.setStatus(UserStatus.ACTIVE);
      users.save(user);
      LOG.info("Seeded {} user {}", label, username);
      return;
    }

    var user = existing.get();
    boolean changed = false;
    if (user.getRole() != role) {
      user.setRole(role);
      changed = true;
    }
    if (user.getStatus() != UserStatus.ACTIVE) {
      user.setStatus(UserStatus.ACTIVE);
      changed = true;
    }
    if (user.getCustomerId() != null) {
      user.setCustomerId(null);
      changed = true;
    }
    if (user.getMobilePinHash() != null) {
      user.setMobilePinHash(null);
      changed = true;
    }
    if (user.isPermissionsCustomized()) {
      jdbc.update("delete from user_permissions where user_id=?", user.getId().toString());
      user.setPermissionsCustomized(false);
      changed = true;
    }
    if (!passwords.matches(password, user.getPasswordHash())) {
      user.setPasswordHash(passwords.encode(password));
      user.setTokenVersion(user.getTokenVersion() + 1);
      changed = true;
    }
    if (changed) {
      users.save(user);
      LOG.info("Repaired {} seed user {}", label, username);
    }
  }
}
