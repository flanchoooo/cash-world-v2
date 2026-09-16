package com.poscloud.wallet.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.poscloud.wallet.auth.*;
import com.poscloud.wallet.common.Types.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  private SecretKeySpec key(String secret) {
    if (secret.getBytes(StandardCharsets.UTF_8).length < 32)
      throw new IllegalArgumentException("JWT_SECRET must contain at least 32 bytes");
    return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }

  @Bean
  JwtEncoder jwtEncoder(@Value("${wallet.jwt-secret}") String secret) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(key(secret)));
  }

  @Bean
  JwtDecoder jwtDecoder(
      @Value("${wallet.jwt-secret}") String secret, @Value("${wallet.issuer}") String issuer) {
    var decoder =
        NimbusJwtDecoder.withSecretKey(key(secret)).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
    return decoder;
  }

  @Bean
  SecurityFilterChain filter(
      HttpSecurity http,
      UserRepository users,
      @Value("${springdoc.api-docs.enabled:false}") boolean docs)
      throws Exception {
    http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    http.authorizeHttpRequests(
        a -> {
          a.requestMatchers(
                  "/api/auth/login",
                  "/api/external/login",
                  "/api/auth/refresh",
                  "/api/auth/browser/login",
                  "/api/auth/browser/refresh",
                  "/api/auth/browser/logout",
                  "/api/remittances/public/receipts/**",
                  "/actuator/health")
              .permitAll();
          if (docs)
            a.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
          a.anyRequest().authenticated();
        });
    http.oauth2ResourceServer(
        o ->
            o.jwt(
                j ->
                    j.jwtAuthenticationConverter(
                        jwt -> {
                          User u;
                          try {
                            u = users.findById(UUID.fromString(jwt.getSubject())).orElse(null);
                          } catch (Exception e) {
                            throw new OAuth2AuthenticationException("invalid_token");
                          }
                          if (u == null
                              || u.getStatus() != UserStatus.ACTIVE
                              || !"access".equals(jwt.getClaimAsString("kind"))
                              || !Long.valueOf(u.getTokenVersion()).equals(jwt.getClaim("ver")))
                            throw new OAuth2AuthenticationException("invalid_token");
                          return new JwtAuthenticationToken(
                              jwt,
                              List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole())),
                              u.getId().toString());
                        })));
    http.exceptionHandling(
        e ->
            e.authenticationEntryPoint(
                    (req, res, ex) -> {
                      res.setStatus(401);
                      res.setContentType("application/json");
                      res.getWriter()
                          .write(
                              "{\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication required\"}");
                    })
                .accessDeniedHandler(
                    (req, res, ex) -> {
                      res.setStatus(403);
                      res.setContentType("application/json");
                      res.getWriter()
                          .write("{\"code\":\"FORBIDDEN\",\"message\":\"Access denied\"}");
                    }));
    return http.build();
  }
}
