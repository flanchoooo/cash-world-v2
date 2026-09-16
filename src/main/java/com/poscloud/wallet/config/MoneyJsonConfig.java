package com.poscloud.wallet.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.*;

@Configuration
public class MoneyJsonConfig {
  // Decimal strings preserve exact values through MySQL JSON and JavaScript clients.
  @Bean
  Jackson2ObjectMapperBuilderCustomizer moneySerialization() {
    return b -> b.serializerByType(BigDecimal.class, ToStringSerializer.instance);
  }
}
