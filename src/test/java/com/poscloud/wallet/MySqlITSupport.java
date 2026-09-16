package com.poscloud.wallet;

import com.poscloud.wallet.auth.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

@SpringBootTest
public abstract class MySqlITSupport {
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>("mysql:8.4").withCommand("--log-bin-trust-function-creators=1");

  static {
    MYSQL.start();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", MYSQL::getJdbcUrl);
    r.add("spring.datasource.username", MYSQL::getUsername);
    r.add("spring.datasource.password", MYSQL::getPassword);
    r.add("wallet.jwt-secret", () -> "test-secret-with-at-least-thirty-two-bytes");
    r.add("wallet.bootstrap.username", () -> "test-admin");
    r.add("wallet.bootstrap.password", () -> "test-admin-password");
    r.add("wallet.remittance.rates", () -> "USD:BWP=13.500000");
    r.add("wallet.mock-provider-enabled", () -> "true");
    r.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/dev");
  }

  @Autowired protected UserRepository users;

  @BeforeEach
  void authenticate() {
    var u = users.findByUsername("test-admin").orElseThrow();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                u.getId().toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));
  }
}
