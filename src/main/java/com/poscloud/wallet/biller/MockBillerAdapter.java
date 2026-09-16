package com.poscloud.wallet.biller;

import com.poscloud.wallet.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MockBillerAdapter implements BillerAdapter {
  private final boolean enabled;

  public MockBillerAdapter(@Value("${wallet.mock-provider-enabled:false}") boolean enabled) {
    this.enabled = enabled;
  }

  private void check() {
    ApiException.require(enabled, "PROVIDER_NOT_CONFIGURED");
  }

  public boolean validateCustomer(String product, String customer) {
    check();
    return customer != null && !customer.isBlank() && !customer.equals("INVALID");
  }

  public Result purchase(Purchase r) {
    check();
    return new Result(
        r.customerReference().equals("FAIL")
            ? State.FAILED
            : r.customerReference().equals("PENDING") || r.customerReference().equals("TIMEOUT")
                ? State.PENDING
                : State.SUCCESS,
        "MOCK-" + r.transactionReference(),
        "Mock provider response");
  }

  public Result enquire(String ref, String customer) {
    check();
    return new Result(
        customer.equals("FAIL") ? State.FAILED : State.SUCCESS,
        "MOCK-" + ref,
        "Mock enquiry response");
  }

  public Result reverse(String reversal, String original, String provider) {
    check();
    return new Result(State.SUCCESS, "MOCK-" + reversal, "Mock reversed");
  }
}
