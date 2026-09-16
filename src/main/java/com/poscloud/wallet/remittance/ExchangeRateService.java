package com.poscloud.wallet.remittance;

import com.poscloud.wallet.common.ApiException;
import com.poscloud.wallet.currency.CurrencyRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExchangeRateService {
  private final CurrencyRepository currencies;

  public BigDecimal rate(String source, String destination) {
    if (source.equals(destination)) return BigDecimal.ONE;
    var from =
        currencies.findByCode(source).orElseThrow(() -> new ApiException("INVALID_CURRENCY"));
    var to =
        currencies.findByCode(destination).orElseThrow(() -> new ApiException("INVALID_CURRENCY"));
    ApiException.require(
        from.getRateAgainstUsd() != null
            && from.getRateAgainstUsd().signum() > 0
            && to.getRateAgainstUsd() != null
            && to.getRateAgainstUsd().signum() > 0,
        "FX_RATE_NOT_CONFIGURED");
    return to.getRateAgainstUsd().divide(from.getRateAgainstUsd(), 6, RoundingMode.HALF_UP);
  }
}
