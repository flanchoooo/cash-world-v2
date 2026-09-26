package com.poscloud.wallet.config;

import com.poscloud.wallet.common.Types.Status;
import com.poscloud.wallet.currency.Currency;
import com.poscloud.wallet.currency.CurrencyRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class BootstrapCurrencies implements ApplicationRunner {
  private record DefaultCurrency(String code, String name, String symbol) {}

  private static final List<DefaultCurrency> DEFAULTS =
      List.of(
          new DefaultCurrency("ZAR", "South African Rand", "R"),
          new DefaultCurrency("ZWG", "Zimbabwe Gold", "ZiG"),
          new DefaultCurrency("USD", "US Dollar", "$"),
          new DefaultCurrency("BWP", "Botswana Pula", "P"));

  private final CurrencyRepository currencies;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    for (var defaults : DEFAULTS) {
      if (currencies.findByCode(defaults.code()).isPresent()) continue;
      var currency = new Currency();
      currency.setCode(defaults.code());
      currency.setName(defaults.name());
      currency.setSymbol(defaults.symbol());
      currency.setDecimalPlaces(2);
      currency.setRateAgainstUsd(BigDecimal.ONE);
      currency.setStatus(Status.ACTIVE);
      currencies.save(currency);
    }
  }
}
