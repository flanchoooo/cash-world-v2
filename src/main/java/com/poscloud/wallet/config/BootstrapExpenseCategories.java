package com.poscloud.wallet.config;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class BootstrapExpenseCategories implements ApplicationRunner {
  private static final List<String> DEFAULTS =
      List.of("Travel", "Office supplies", "Utilities", "Rent", "Staff welfare", "Other");

  private final JdbcTemplate jdbc;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    for (var name : DEFAULTS) {
      if (jdbc.queryForObject(
              "select count(*) from expense_categories where name=?", Integer.class, name)
          > 0) continue;
      var now = Timestamp.from(Instant.now());
      jdbc.update(
          "insert into expense_categories(id,created_at,updated_at,name,status) values (UUID(),?,?,?,'ACTIVE')",
          now,
          now,
          name);
    }
  }
}
