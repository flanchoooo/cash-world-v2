package com.poscloud.wallet.config;

import com.poscloud.wallet.auth.*;
import com.poscloud.wallet.common.Types.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class BootstrapAdmin implements ApplicationRunner {
  private final UserRepository users;
  private final PasswordEncoder passwords;

  @Value("${wallet.bootstrap.username:}")
  private String username;

  @Value("${wallet.bootstrap.password:}")
  private String password;

  @Transactional
  @Override
  public void run(ApplicationArguments args) {
    if (username.isBlank() || users.findByUsername(username).isPresent()) return;
    if (password.length() < 8)
      throw new IllegalStateException("Bootstrap password must be 8+ characters");
    var u = new User();
    u.setUsername(username);
    u.setPasswordHash(passwords.encode(password));
    u.setRole(Role.SUPER_ADMIN);
    u.setStatus(UserStatus.ACTIVE);
    users.save(u);
  }
}
