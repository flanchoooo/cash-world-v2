package com.poscloud.wallet.auth;

import com.poscloud.wallet.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URI;
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

  private void verifyOrigin(String origin, HttpServletRequest request) {
    ApiException.require(origin != null && !origin.isBlank(), "FORBIDDEN");
    for (String configured : allowedOrigin.split(",")) {
      if (configured.trim().equals(origin)) return;
    }
    try {
      URI browser = URI.create(origin);
      String host = request.getHeader(HttpHeaders.HOST);
      String forwardedProto = request.getHeader("X-Forwarded-Proto");
      String scheme = "https".equalsIgnoreCase(forwardedProto) ? "https" : request.getScheme();
      URI served = URI.create(scheme + "://" + host);
      ApiException.require(
          browser.getRawUserInfo() == null
              && (browser.getRawPath() == null || browser.getRawPath().isEmpty())
              && browser.getRawQuery() == null
              && browser.getRawFragment() == null
              && browser.getScheme() != null
              && browser.getScheme().equalsIgnoreCase(served.getScheme())
              && browser.getHost() != null
              && browser.getHost().equalsIgnoreCase(served.getHost())
              && effectivePort(browser) == effectivePort(served),
          "FORBIDDEN");
    } catch (IllegalArgumentException e) {
      throw new ApiException("FORBIDDEN");
    }
  }

  private int effectivePort(URI uri) {
    if (uri.getPort() != -1) return uri.getPort();
    return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
  }

  private void cookie(HttpServletRequest request, HttpServletResponse response, String value, Duration age) {
    boolean useSecureCookie =
        secure || request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from(COOKIE, value)
            .httpOnly(true)
            .secure(useSecureCookie)
            .sameSite("Strict")
            .path("/api/auth/browser")
            .maxAge(age)
            .build()
            .toString());
    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
  }

  private Session session(AuthService.Tokens tokens, HttpServletRequest request, HttpServletResponse response) {
    cookie(request, response, tokens.refreshToken(), Duration.ofDays(7));
    return new Session(tokens.accessToken(), tokens.expiresIn(), tokens.user());
  }

  @PostMapping("/login")
  public Session login(
      @RequestHeader(value = "Origin", required = false) String origin,
      @Valid @RequestBody AuthService.Login request,
      HttpServletRequest httpRequest,
      HttpServletResponse response) {
    verifyOrigin(origin, httpRequest);
    return session(auth.browserLogin(request), httpRequest, response);
  }

  @PostMapping("/refresh")
  public Session refresh(
      @RequestHeader(value = "Origin", required = false) String origin,
      @CookieValue(value = COOKIE, required = false) String token,
      HttpServletRequest httpRequest,
      HttpServletResponse response) {
    verifyOrigin(origin, httpRequest);
    ApiException.require(token != null && !token.isBlank(), "UNAUTHORIZED");
    try {
      return session(auth.browserRefresh(new AuthService.Refresh(token)), httpRequest, response);
    } catch (ApiException e) {
      cookie(httpRequest, response, "", Duration.ZERO);
      throw e;
    }
  }

  @PostMapping("/logout")
  public void logout(
      @RequestHeader(value = "Origin", required = false) String origin,
      @CookieValue(value = COOKIE, required = false) String token,
      HttpServletRequest httpRequest,
      HttpServletResponse response) {
    verifyOrigin(origin, httpRequest);
    if (token != null && !token.isBlank()) auth.logout(new AuthService.Refresh(token));
    cookie(httpRequest, response, "", Duration.ZERO);
  }
}
