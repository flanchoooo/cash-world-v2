package com.poscloud.wallet.config;

import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.util.List;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExternalWalletOpenApiConfig {
  @Bean
  GroupedOpenApi externalWalletOpenApi() {
    return GroupedOpenApi.builder()
        .group("external-wallet")
        .displayName("External Wallet API")
        .pathsToMatch("/api/external/**", "/api/auth/refresh")
        .addOpenApiCustomizer(
            api -> {
              api.info(
                  new Info()
                      .title("Poscloud External Wallet API")
                      .description(
                          "Customer and agent wallet API for balances, allocated products, sales, transaction history and reversals. Call login, copy accessToken, click Authorize, and paste the token without adding the Bearer prefix.")
                      .version("1.0.0"));
              api.getPaths()
                  .forEach(
                      (path, item) ->
                          item.readOperations()
                              .forEach(
                                  operation ->
                                      operation.setSecurity(
                                          path.equals("/api/external/login")
                                                  || path.equals("/api/auth/refresh")
                                              ? List.of()
                                              : List.of(
                                                  new SecurityRequirement()
                                                      .addList("bearerAuth")))));
            })
        .build();
  }
}
