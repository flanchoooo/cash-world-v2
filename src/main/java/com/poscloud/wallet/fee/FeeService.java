package com.poscloud.wallet.fee;

import com.poscloud.wallet.common.*;
import com.poscloud.wallet.common.Types.*;
import com.poscloud.wallet.currency.Currency;
import com.poscloud.wallet.customer.Customer;
import com.poscloud.wallet.transaction.TransactionTypeRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FeeService {
  private final FeeRepository fees;
  private final TransactionTypeRepository types;

  public BigDecimal calculateFee(
      String type, UUID product, Customer customer, Currency currency, BigDecimal amount) {
    var transaction =
        types.findByCode(type).orElseThrow(() -> new ApiException("TRANSACTION_TYPE_NOT_FOUND"));
    ApiException.require(transaction.getStatus() == Status.ACTIVE, "TRANSACTION_TYPE_NOT_FOUND");
    if (!transaction.isAllowsFee()) return BigDecimal.ZERO;
    var now = Instant.now();
    var selected =
        fees.findByTransactionTypeIdAndCurrencyId(transaction.getId(), currency.getId()).stream()
            .filter(
                f ->
                    f.getStatus() == Status.ACTIVE
                        && !f.getEffectiveFrom().isAfter(now)
                        && (f.getEffectiveTo() == null || f.getEffectiveTo().isAfter(now)))
            .filter(f -> f.getBillerProductId() == null || f.getBillerProductId().equals(product))
            .filter(
                f ->
                    f.getCustomerType() == null
                        || customer != null && f.getCustomerType() == customer.getCustomerType())
            .filter(
                f ->
                    f.getAgentOnly() == null
                        || f.getAgentOnly().equals(customer != null && customer.isAgent()))
            .filter(
                f ->
                    f.getMinTransactionAmount() == null
                        || amount.compareTo(f.getMinTransactionAmount()) >= 0)
            .filter(
                f ->
                    f.getMaxTransactionAmount() == null
                        || amount.compareTo(f.getMaxTransactionAmount()) <= 0)
            .sorted(
                Comparator.comparingInt(Fee::getPriority)
                    .reversed()
                    .thenComparing(Comparator.comparingInt(this::specificity).reversed())
                    .thenComparing(f -> f.getId().toString()))
            .findFirst()
            .orElseThrow(() -> new ApiException("FEE_NOT_CONFIGURED"));
    return compute(selected, amount, currency.getDecimalPlaces());
  }

  private int specificity(Fee f) {
    return (f.getBillerProductId() != null ? 4 : 0)
        + (f.getCustomerType() != null ? 2 : 0)
        + (f.getAgentOnly() != null ? 1 : 0);
  }

  public BigDecimal compute(Fee f, BigDecimal amount, int places) {
    var result = BigDecimal.ZERO;
    if (f.getCalculationType() != CalculationType.PERCENTAGE)
      result = result.add(zero(f.getFixedAmount()));
    if (f.getCalculationType() != CalculationType.FIXED)
      result = result.add(amount.multiply(zero(f.getPercentage())).movePointLeft(2));
    if (f.getMinimumFee() != null) result = result.max(f.getMinimumFee());
    if (f.getMaximumFee() != null) result = result.min(f.getMaximumFee());
    return Money.round(result, places);
  }

  private BigDecimal zero(BigDecimal n) {
    return n == null ? BigDecimal.ZERO : n;
  }
}
