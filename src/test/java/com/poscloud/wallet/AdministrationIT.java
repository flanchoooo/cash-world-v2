package com.poscloud.wallet;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.poscloud.wallet.admin.*;
import com.poscloud.wallet.auth.*;
import com.poscloud.wallet.common.Types.*;
import com.poscloud.wallet.customer.*;
import com.poscloud.wallet.transaction.*;
import com.poscloud.wallet.wallet.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AdministrationIT extends MySqlITSupport {
  @Autowired MockMvc mvc;
  @Autowired AuthService auth;
  @Autowired CustomerService customers;
  @Autowired WalletService wallets;
  @Autowired TransferService transfers;
  @Autowired AdministrationService admin;

  String token(Role role, UUID customer) {
    String name = "workspace-" + UUID.randomUUID();
    auth.register(new AuthService.Register(name, "long-test-password", customer, role, null, null));
    return auth.login(new AuthService.Login(name, "long-test-password")).accessToken();
  }

  CustomerService.View corporate() {
    return customers.create(
        CustomerType.CORPORATE,
        new CustomerService.Request(
            null, null, "Company " + UUID.randomUUID(), "REG", null, "123456", null, null));
  }

  @Test
  void staffPagesAreBoundedSearchableAndRedacted() throws Exception {
    var c = customers.create(CustomerType.INDIVIDUAL, IdentityIT.individual());
    String token = token(Role.SUPER_ADMIN, null);
    mvc.perform(
            get("/api/admin/workspace/customers")
                .param("search", c.customerNumber())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("total").value(1))
        .andExpect(jsonPath("items[0].customerNumber").value(c.customerNumber()))
        .andExpect(jsonPath("items[0].nationalId").doesNotExist());
    for (String resource :
        List.of(
            "customers",
            "wallets",
            "users",
            "transactions",
            "bill-payments",
            "remittances",
            "audit"))
      mvc.perform(
              get("/api/admin/workspace/" + resource).header("Authorization", "Bearer " + token))
          .andExpect(status().isOk());
    mvc.perform(get("/api/admin/workspace/users").header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("items[0].passwordHash").doesNotExist());
    mvc.perform(
            get("/api/admin/workspace/wallets")
                .param("size", "101")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/admin/workspace/customers")
                .param("search", "' OR 1=1 --")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("total").value(0));
  }

  @Test
  void corporateScopeCannotBeChangedWithSearch() throws Exception {
    var c = corporate();
    var other = corporate();
    var own = wallets.create(new WalletService.Create(c.id(), "USD", "Owned"));
    var privateWallet = wallets.create(new WalletService.Create(other.id(), "USD", "Private"));
    String token = token(Role.CORPORATE_ADMIN, c.id());
    mvc.perform(get("/api/admin/workspace/wallets").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("total").value(1))
        .andExpect(jsonPath("items[0].walletNumber").value(own.walletNumber()));
    mvc.perform(
            get("/api/admin/workspace/wallets")
                .param("search", privateWallet.walletNumber())
                .header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("total").value(0));
    for (String resource : List.of("customers", "transactions", "audit"))
      mvc.perform(
              get("/api/admin/workspace/" + resource).header("Authorization", "Bearer " + token))
          .andExpect(status().isForbidden());
    mvc.perform(get("/api/admin/workspace/dashboard").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("wallets").value(1))
        .andExpect(jsonPath("balances[0].currency").value("USD"));
    mvc.perform(get("/api/admin/workspace/catalog").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  void retailAndOperationsCannotListUsers() throws Exception {
    var c = customers.create(CustomerType.INDIVIDUAL, IdentityIT.individual());
    String retail = token(Role.CUSTOMER, c.id());
    mvc.perform(get("/api/admin/workspace/wallets").header("Authorization", "Bearer " + retail))
        .andExpect(status().isForbidden());
    authenticate();
    String operations = token(Role.OPERATIONS, null);
    mvc.perform(get("/api/admin/workspace/users").header("Authorization", "Bearer " + operations))
        .andExpect(status().isForbidden());
  }

  @Test
  void dashboardKeepsCurrenciesSeparateAndTransactionsIncludeLedgerResults() {
    var c = corporate();
    var usd = wallets.create(new WalletService.Create(c.id(), "USD", "Dollar"));
    wallets.create(new WalletService.Create(c.id(), "BWP", "Pula"));
    var tx =
        transfers.deposit(
            UUID.randomUUID().toString(),
            new TransferService.Cash(usd.walletNumber(), new BigDecimal("12.34")));
    var page =
        admin.list("transactions", tx.transactionReference(), "SUCCESS", null, null, 0, 20, false);
    assertThat(page.total()).isEqualTo(1);
    assertThat(page.items().get(0).get("currency")).isEqualTo("USD");
    assertThat(page.items().get(0).get("faceValue").toString()).startsWith("12.34");
    assertThat(admin.dashboard().balances())
        .extracting(r -> r.get("currency"))
        .contains("USD", "BWP");
  }

  @Test
  void customerEditPreservesRedactedIdentity() {
    var c =
        customers.create(
            CustomerType.INDIVIDUAL,
            new CustomerService.Request(
                "Ada", "Test", null, null, "SECRET-ID", "1234", null, null));
    customers.update(
        c.customerNumber(),
        new CustomerService.Request("Ada", "Changed", null, null, null, "1234", null, null));
    assertThat(customers.find(c.customerNumber()).getNationalId()).isEqualTo("SECRET-ID");
  }

  @Test
  void corporateUserAccessCannotEscalateOrCrossTenants() throws Exception {
    var c = corporate();
    var other = corporate();
    var own =
        auth.register(
            new AuthService.Register(
                "own-" + UUID.randomUUID(),
                "long-test-password",
                c.id(),
                Role.CORPORATE_USER,
                null,
                null));
    var foreign =
        auth.register(
            new AuthService.Register(
                "foreign-" + UUID.randomUUID(),
                "long-test-password",
                other.id(),
                Role.CORPORATE_USER,
                null,
                null));
    var token = token(Role.CORPORATE_ADMIN, c.id());
    mvc.perform(
            put("/api/auth/users/" + own.id())
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"role\":\"SUPER_ADMIN\",\"status\":\"ACTIVE\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/auth/users/" + foreign.id())
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"status\":\"BLOCKED\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/auth/users/" + own.id())
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"status\":\"BLOCKED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("status").value("BLOCKED"));
  }

  @Test
  void staffCanChangeAccessAndCannotDisableSelf() throws Exception {
    var user =
        auth.register(
            new AuthService.Register(
                "change-" + UUID.randomUUID(),
                "long-test-password",
                null,
                Role.OPERATIONS,
                null,
                null));
    var oldToken =
        auth.login(new AuthService.Login(user.username(), "long-test-password")).accessToken();
    auth.updateUser(user.id(), new AuthService.UpdateUser(Role.SUPER_ADMIN, UserStatus.ACTIVE));
    mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + oldToken))
        .andExpect(status().isUnauthorized());
    authenticate();
    var self = auth.me();
    assertThatThrownBy(
            () -> auth.updateUser(self.id(), new AuthService.UpdateUser(null, UserStatus.BLOCKED)))
        .hasMessage("CANNOT_CHANGE_OWN_ACCESS");
  }
}
