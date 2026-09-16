package com.poscloud.wallet;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poscloud.wallet.auth.AuthService;
import com.poscloud.wallet.common.Types.*;
import com.poscloud.wallet.customer.CustomerService;
import com.poscloud.wallet.wallet.WalletService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@org.springframework.test.context.TestPropertySource(
    properties = {"springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"})
@AutoConfigureMockMvc
class ApiSecurityIT extends MySqlITSupport {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired AuthService auth;
  @Autowired CustomerService customers;
  @Autowired WalletService wallets;

  String token(AuthService.UserView user, String username) {
    return auth.login(new AuthService.Login(username, "long-test-password")).accessToken();
  }

  @Test
  void bearerTokenAndBlockedUser() throws Exception {
    var username = "api-" + UUID.randomUUID();
    var u =
        auth.register(
            new AuthService.Register(
                username, "long-test-password", null, Role.OPERATIONS, null, null));
    String token = token(u, username);
    mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("username").value(username))
        .andExpect(jsonPath("passwordHash").doesNotExist());
    authenticate();
    auth.block(u.id());
    mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void customerCannotReadOthersWalletOrCreateMoney() throws Exception {
    var c = customers.create(CustomerType.INDIVIDUAL, IdentityIT.individual());
    var other = customers.create(CustomerType.INDIVIDUAL, IdentityIT.individual());
    var w = wallets.create(new WalletService.Create(other.id(), "USD", "Private"));
    String username = "owner-" + UUID.randomUUID();
    var u =
        auth.register(
            new AuthService.Register(
                username, "long-test-password", c.id(), Role.CUSTOMER, null, null));
    String token = token(u, username);
    mvc.perform(get("/api/wallets/" + w.walletNumber()).header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/wallets/deposit")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"walletNumber\":\"" + w.walletNumber() + "\",\"amount\":100}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void anonymousAndInvalidInputHaveConsistentErrors() throws Exception {
    org.springframework.security.core.context.SecurityContextHolder.clearContext();
    mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\",\"password\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("code").value("INVALID_REQUEST"));
  }

  @Test
  void corporateAdminCannotAttachUserToOtherCustomer() {
    var c =
        customers.create(
            CustomerType.CORPORATE,
            new CustomerService.Request(null, null, "Acme", "REG", null, "123", null, null));
    String username = "corp-admin-" + UUID.randomUUID();
    var u =
        auth.register(
            new AuthService.Register(
                username, "long-test-password", c.id(), Role.CORPORATE_ADMIN, null, null));
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                u.id().toString(), null, List.of()));
    assertThatThrownBy(
            () ->
                auth.register(
                    new AuthService.Register(
                        "illegal-" + UUID.randomUUID(),
                        "long-test-password",
                        UUID.randomUUID(),
                        Role.CORPORATE_USER,
                        null,
                        null)))
        .hasMessage("FORBIDDEN");
    assertThat(
            auth.register(
                    new AuthService.Register(
                        "valid-" + UUID.randomUUID(),
                        "long-test-password",
                        c.id(),
                        Role.CORPORATE_USER,
                        null,
                        null))
                .customerId())
        .isEqualTo(c.id());
  }

  @Test
  void openApiUsesDistinctDtoSchemas() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/wallets/send-money']").exists())
        .andExpect(
            jsonPath("$.components.schemas.TransferServiceSend.properties.sourceWalletNumber")
                .exists())
        .andExpect(
            jsonPath("$.components.schemas.BillPaymentServiceRequest.properties.productCode")
                .exists());
  }
}
