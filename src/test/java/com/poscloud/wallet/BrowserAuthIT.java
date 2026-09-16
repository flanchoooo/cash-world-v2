package com.poscloud.wallet;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poscloud.wallet.auth.*;
import com.poscloud.wallet.common.Types.*;
import com.poscloud.wallet.customer.CustomerService;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "wallet.browser.allowed-origin=http://localhost:5173",
      "wallet.browser.secure-cookie=true"
    })
class BrowserAuthIT extends MySqlITSupport {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired AuthService auth;
  @Autowired CustomerService customers;
  static final String ORIGIN = "http://localhost:5173";
  static final String PASSWORD = "long-test-password";

  String operator() {
    String username = "browser-" + UUID.randomUUID();
    auth.register(new AuthService.Register(username, PASSWORD, null, Role.OPERATIONS, null, null));
    return username;
  }

  MvcResult login(String username) throws Exception {
    return mvc.perform(
            post("/api/auth/browser/login")
                .header("Origin", ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AuthService.Login(username, PASSWORD))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("refreshToken").doesNotExist())
        .andReturn();
  }

  Cookie cookie(MvcResult result) {
    return result.getResponse().getCookie(BrowserAuthController.COOKIE);
  }

  @Test
  void cookieIsHttpOnlySecureScopedAndRotates() throws Exception {
    var original = login(operator());
    String setCookie = original.getResponse().getHeader("Set-Cookie");
    assertThat(setCookie)
        .contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/auth/browser");
    assertThat(original.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
    var next =
        mvc.perform(
                post("/api/auth/browser/refresh").header("Origin", ORIGIN).cookie(cookie(original)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("refreshToken").doesNotExist())
            .andReturn();
    assertThat(cookie(next).getValue()).isNotEqualTo(cookie(original).getValue());
    mvc.perform(post("/api/auth/browser/refresh").header("Origin", ORIGIN).cookie(cookie(original)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void logoutRevokesRefreshAndAccessToken() throws Exception {
    var login = login(operator());
    var token = json.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
    mvc.perform(post("/api/auth/browser/logout").header("Origin", ORIGIN).cookie(cookie(login)))
        .andExpect(status().isOk())
        .andExpect(
            header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
    mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/auth/browser/refresh").header("Origin", ORIGIN).cookie(cookie(login)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void missingAndUntrustedOriginsAreRejected() throws Exception {
    mvc.perform(post("/api/auth/browser/logout")).andExpect(status().isForbidden());
    mvc.perform(post("/api/auth/browser/refresh").header("Origin", "https://untrusted.example"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/auth/browser/login")
                .header("Origin", "http://localhost:5173.evil.example")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"anything\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void retailAccountCannotOpenAdministrationSession() throws Exception {
    var c = customers.create(CustomerType.INDIVIDUAL, IdentityIT.individual());
    String username = "retail-" + UUID.randomUUID();
    auth.register(new AuthService.Register(username, PASSWORD, c.id(), Role.CUSTOMER, null, null));
    mvc.perform(
            post("/api/auth/browser/login")
                .header("Origin", ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AuthService.Login(username, PASSWORD))))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist("Set-Cookie"));
  }

  @Test
  void blockedUserCannotRenewBrowserSession() throws Exception {
    var username = operator();
    var login = login(username);
    authenticate();
    auth.block(users.findByUsername(username).orElseThrow().getId());
    mvc.perform(post("/api/auth/browser/refresh").header("Origin", ORIGIN).cookie(cookie(login)))
        .andExpect(status().isUnauthorized());
  }
}
