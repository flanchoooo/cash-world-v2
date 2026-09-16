package com.poscloud.wallet.biller;

import java.math.BigDecimal;
import java.util.Map;

/**
 * A live implementation must provide durable provider idempotency and reconciliation before
 * enabling purchases.
 */
public interface BillerAdapter {
  record Purchase(
      String transactionReference,
      String productCode,
      String customerReference,
      BigDecimal amount) {}

  enum State {
    SUCCESS,
    FAILED,
    PENDING
  }

  record Result(
      State status,
      String providerReference,
      String message,
      Map<String, Object> metadata) {
    public Result(State status, String providerReference, String message) {
      this(status, providerReference, message, Map.of());
    }

    public Result {
      metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
  }

  boolean validateCustomer(String productCode, String customerReference);

  Result purchase(Purchase request);

  Result enquire(String transactionReference, String customerReference);

  Result reverse(String reversalReference, String originalReference, String providerReference);
}
