package com.poscloud.wallet.common;

import java.math.*;

public final class Money {
  private Money() {}

  public static final BigDecimal MAX = new BigDecimal("999999999999999.9999");

  public static BigDecimal amount(BigDecimal value, int places) {
    ApiException.require(
        value != null && value.signum() > 0 && value.compareTo(MAX) <= 0, "INVALID_AMOUNT");
    try {
      return value.setScale(places, RoundingMode.UNNECESSARY);
    } catch (ArithmeticException e) {
      throw new ApiException("INVALID_AMOUNT", "Amount exceeds currency precision");
    }
  }

  public static BigDecimal round(BigDecimal value, int places) {
    return value.setScale(places, RoundingMode.HALF_UP);
  }
}
