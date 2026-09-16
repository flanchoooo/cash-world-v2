package com.poscloud.wallet.auth;

import com.poscloud.wallet.common.ApiException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

/**
 * Same-origin browser session transport; existing API clients keep their bearer-token endpoints.
 */
@RestController
@RequestMapping("/api/auth/browser")
public class BrowserAuthController {
  public static final String COOKIE = "poscloud_refresh";
  private final AuthService auth;
  private final String allowedOrigin;
  private final boolean secure;

  public BrowserAuthController(
      AuthService auth,
      @Value("${wallet.browser.allowed-origin:}") String allowedOrigin,
      @Value("${wallet.browser.secure-cookie:true}") boolean secure) {
    this.auth = auth;
    this.allowedOrigin = allowedOrigin;
    this.secure = secure;
  }

  public record Session(String accessToken, long expiresIn, AuthService.UserView user) {}

  private void verifyOrigin(String origin) {
    ApiException.require(!allowedOrigin.isBlank() && allowedOrigin.equals(origin), "FORBIDDEN");
  }

  private void cookie(HttpServletResponse response, String value, Duration age) {
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from(COOKIE, value)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Strict")
            .path("/api/auth/browser")
            .maxAge(age)
            .build()
            .toString());
    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
  }

  private Session session(AuthService.Tokens tokens, HttpServletResponse response) {
    cookie(response, tokens.refreshToken(), Duration.ofDays(7));
    return new Session(tokens.accessToken(), tokens.expiresIn(), tokens.user());
  }

  @PostMapping("/login")
  public Session login(
      @RequestHeader(value = "Origin", required = false) String origin,
      @Valid @RequestBody AuthService.Login request,
      HttpServletResponse response) {
    verifyOrigin(origin);
    return session(auth.browserLogin(request), response);
  }

  @PostMapping("/refresh")
  public Session refresh(
      @RequestHeader(value = "Origin", required = false) String origin,
      @CookieValue(value = COOKIE, required = false) String token,
      HttpServletResponse response) {
    verifyOrigin(origin);
    ApiException.require(token != null && !token.isBlank(), "UNAUTHORIZED");
    try {
      return session(auth.browserRefresh(new AuthService.Refresh(token)), response);
    } catch (ApiException e) {
      cookie(response, "", Duration.ZERO);
      throw e;
    }
  }

  @PostMapping("/logout")
  public void logout(
      @RequestHeader(value = "Origin", required = false) String origin,
      @CookieValue(value = COOKIE, required = false) String token,
      HttpServletResponse response) {
    verifyOrigin(origin);
    if (token != null && !token.isBlank()) auth.logout(new AuthService.Refresh(token));
    cookie(response, "", Duration.ZERO);
  }
}
