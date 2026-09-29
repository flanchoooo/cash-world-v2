package com.poscloud.wallet.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.poscloud.wallet.auth.User;
import com.poscloud.wallet.auth.UserRepository;
import com.poscloud.wallet.common.Types.Role;
import com.poscloud.wallet.common.Types.UserStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class BootstrapUsersTest {
  @Mock private UserRepository users;
  @Mock private PasswordEncoder passwords;
  @Mock private JdbcTemplate jdbc;

  @Test
  void createsConfiguredSeedUsersWhenMissing() {
    when(users.findByUsername("admin")).thenReturn(Optional.empty());
    when(users.findByUsername("CUS00000001")).thenReturn(Optional.empty());
    when(passwords.encode("admin-password")).thenReturn("admin-hash");
    when(passwords.encode("service-password")).thenReturn("service-hash");

    var bootstrap =
        new BootstrapUsers(
            users,
            passwords,
            jdbc,
            "admin",
            "admin-password",
            "CUS00000001",
            "service-password");

    bootstrap.run(null);

    var saved = ArgumentCaptor.forClass(User.class);
    verify(users, org.mockito.Mockito.times(2)).save(saved.capture());
    assertThat(saved.getAllValues())
        .extracting(User::getUsername, User::getRole, User::getStatus, User::getPasswordHash)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(
                "admin", Role.SUPER_ADMIN, UserStatus.ACTIVE, "admin-hash"),
            org.assertj.core.groups.Tuple.tuple(
                "CUS00000001", Role.ESB_SERVICE, UserStatus.ACTIVE, "service-hash"));
  }

  @Test
  void repairsExistingConfiguredSeedUsers() {
    var existing = new User();
    existing.setUsername("admin");
    existing.setPasswordHash("old-hash");
    existing.setRole(Role.OPERATIONS);
    existing.setStatus(UserStatus.BLOCKED);
    existing.setCustomerId(UUID.randomUUID());
    existing.setMobilePinHash("pin-hash");
    existing.setPermissionsCustomized(true);

    when(users.findByUsername("admin")).thenReturn(Optional.of(existing));
    when(passwords.matches("admin-password", "old-hash")).thenReturn(false);
    when(passwords.encode("admin-password")).thenReturn("new-hash");

    var bootstrap =
        new BootstrapUsers(users, passwords, jdbc, "admin", "admin-password", "", "");

    bootstrap.run(null);

    assertThat(existing.getRole()).isEqualTo(Role.SUPER_ADMIN);
    assertThat(existing.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(existing.getCustomerId()).isNull();
    assertThat(existing.getMobilePinHash()).isNull();
    assertThat(existing.isPermissionsCustomized()).isFalse();
    assertThat(existing.getPasswordHash()).isEqualTo("new-hash");
    assertThat(existing.getTokenVersion()).isEqualTo(1);
    verify(jdbc).update(eq("delete from user_permissions where user_id=?"), any(String.class));
    verify(users).save(existing);
  }

  @Test
  void leavesExistingMatchingSeedUserUnchanged() {
    var existing = new User();
    existing.setUsername("admin");
    existing.setPasswordHash("current-hash");
    existing.setRole(Role.SUPER_ADMIN);
    existing.setStatus(UserStatus.ACTIVE);

    when(users.findByUsername("admin")).thenReturn(Optional.of(existing));
    when(passwords.matches("admin-password", "current-hash")).thenReturn(true);

    var bootstrap =
        new BootstrapUsers(users, passwords, jdbc, "admin", "admin-password", "", "");

    bootstrap.run(null);

    verify(users, never()).save(any());
  }
}
