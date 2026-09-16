package com.poscloud.wallet;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.poscloud.wallet.common.Types.*;
import com.poscloud.wallet.currency.Currency;
import com.poscloud.wallet.customer.Customer;
import com.poscloud.wallet.fee.*;
import com.poscloud.wallet.transaction.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class FeeSelectionTest {
  Fee rule(String fixed, int priority) {
    var f = new Fee();
    f.setStatus(Status.ACTIVE);
    f.setCalculationType(CalculationType.FIXED);
    f.setFixedAmount(new BigDecimal(fixed));
    f.setPriority(priority);
    f.setEffectiveFrom(Instant.now().minusSeconds(100));
    return f;
  }

  @Test
  void matchesProductAgentAmountDatesAndPriority() {
    var repo = mock(FeeRepository.class);
    var types = mock(TransactionTypeRepository.class);
    var type = new TransactionType();
    type.setAllowsFee(true);
    type.setStatus(Status.ACTIVE);
    var currency = new Currency();
    currency.setDecimalPlaces(2);
    var customer = new Customer();
    customer.setCustomerType(CustomerType.INDIVIDUAL);
    customer.setAgent(true);
    UUID product = UUID.randomUUID();
    var base = rule("1", 0);
    var scoped = rule("2", 5);
    scoped.setBillerProductId(product);
    scoped.setCustomerType(CustomerType.INDIVIDUAL);
    scoped.setAgentOnly(true);
    scoped.setMinTransactionAmount(new BigDecimal("10"));
    scoped.setMaxTransactionAmount(new BigDecimal("50"));
    var expired = rule("99", 100);
    expired.setEffectiveTo(Instant.now().minusSeconds(1));
    var future = rule("99", 100);
    future.setEffectiveFrom(Instant.now().plusSeconds(600));
    when(types.findByCode("BILL_PAYMENT")).thenReturn(Optional.of(type));
    when(repo.findByTransactionTypeIdAndCurrencyId(type.getId(), currency.getId()))
        .thenReturn(List.of(expired, base, scoped, future));
    var service = new FeeService(repo, types);
    assertThat(
            service.calculateFee("BILL_PAYMENT", product, customer, currency, new BigDecimal("20")))
        .isEqualByComparingTo("2");
    assertThat(
            service.calculateFee("BILL_PAYMENT", product, customer, currency, new BigDecimal("60")))
        .isEqualByComparingTo("1");
    customer.setAgent(false);
    assertThat(
            service.calculateFee("BILL_PAYMENT", product, customer, currency, new BigDecimal("20")))
        .isEqualByComparingTo("1");
  }
}
