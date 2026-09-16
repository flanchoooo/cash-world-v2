package com.poscloud.wallet;

import static org.assertj.core.api.Assertions.*;

import com.poscloud.wallet.auth.AuthService;
import com.poscloud.wallet.common.Types.*;
import com.poscloud.wallet.customer.CustomerService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class IdentityIT extends MySqlITSupport {
  @Autowired CustomerService customers;
  @Autowired AuthService auth;

  static CustomerService.Request individual() {
    return new CustomerService.Request(
        "Ada", "Lovelace", null, null, "ID123", "263771000001", null, null);
  }

  @Test
  void individualRegistrationAndAgentPromotion() {
    var c = customers.create(CustomerType.INDIVIDUAL, individual());
    assertThat(c.customerNumber()).startsWith("CUS");
    assertThat(customers.agent(c.customerNumber(), AgentType.STANDARD).isAgent()).isTrue();
  }

  @Test
  void corporateSupportsMultipleUsers() {
    var c =
        customers.create(
            CustomerType.CORPORATE,
            new CustomerService.Request(
                null, null, "Acme", "REG1", null, "263771000002", null, null));
    for (int i = 0; i < 2; i++) {
      var u =
          auth.register(
              new AuthService.Register(
                  "corp-" + UUID.randomUUID(),
                  "long-test-password",
                  c.id(),
                  Role.CORPORATE_USER,
                  null,
                  null));
      assertThat(u.customerId()).isEqualTo(c.id());
    }
  }

  @Test
  void invalidCustomerRejected() {
    assertThatThrownBy(() -> customers.create(CustomerType.CORPORATE, individual()))
        .hasMessage("INVALID_CORPORATE");
  }

  @Test
  void refreshRotatesAndBlockingRevokesAccess() {
    String username = "login-" + UUID.randomUUID();
    var u =
        auth.register(
            new AuthService.Register(
                username, "long-test-password", null, Role.OPERATIONS, null, null));
    var tokens = auth.login(new AuthService.Login(username, "long-test-password"));
    var next = auth.refresh(new AuthService.Refresh(tokens.refreshToken()));
    assertThat(next.accessToken()).isNotBlank();
    assertThatThrownBy(() -> auth.refresh(new AuthService.Refresh(tokens.refreshToken())))
        .hasMessage("UNAUTHORIZED");
    auth.block(u.id());
    assertThatThrownBy(() -> auth.refresh(new AuthService.Refresh(next.refreshToken())))
        .hasMessage("UNAUTHORIZED");
  }
}
