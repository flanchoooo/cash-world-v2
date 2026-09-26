package com.poscloud.wallet.config;

import com.poscloud.wallet.auth.User;
import com.poscloud.wallet.auth.UserRepository;
import com.poscloud.wallet.common.Types.Role;
import com.poscloud.wallet.common.Types.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class BootstrapEsbServiceAccount implements ApplicationRunner {
  private final UserRepository users;
  private final PasswordEncoder passwords;

  @Value("${wallet.esb-service.username:CUS00000001}") private String username;
  @Value("${wallet.esb-service.password:CUS00000001}") private String password;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (username.isBlank()) return;
    if (password.length() < 8) throw new IllegalStateException("ESB service password must be 8+ characters");
    var existing = users.findByUsername(username);
    if (existing.isPresent()) {
      var user = existing.get();
      if (user.getRole() != Role.ESB_SERVICE) {
        user.setRole(Role.ESB_SERVICE);
        user.setCustomerId(null);
        user.setMobilePinHash(null);
        user.setPasswordHash(passwords.encode(password));
        user.setStatus(UserStatus.ACTIVE);
        users.save(user);
      }
      return;
    }
    var user = new User();
    user.setUsername(username);
    user.setPasswordHash(passwords.encode(password));
    user.setRole(Role.ESB_SERVICE);
    user.setStatus(UserStatus.ACTIVE);
    users.save(user);
  }
}
