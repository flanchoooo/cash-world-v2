package com.poscloud.wallet;

import static org.assertj.core.api.Assertions.*;

import com.poscloud.wallet.biller.*;
import com.poscloud.wallet.billpayment.*;
import com.poscloud.wallet.commission.*;
import com.poscloud.wallet.common.Types.*;
import com.poscloud.wallet.customer.*;
import com.poscloud.wallet.fee.*;
import com.poscloud.wallet.remittance.*;
import com.poscloud.wallet.transaction.*;
import com.poscloud.wallet.wallet.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class FinancialFlowsIT extends MySqlITSupport {
  @Autowired CustomerService customers;
  @Autowired WalletService wallets;
  @Autowired TransferService transfers;
  @Autowired BillPaymentService bills;
  @Autowired BillerProductRepository products;
  @Autowired BillerProductService productAdmin;
  @Autowired ReversalService reversals;
  @Autowired LedgerEntryRepository ledger;
  @Autowired CommissionService commissions;
  @Autowired FeeRepository feeRepository;
  @Autowired TransactionTypeRepository types;
  @Autowired AdjustmentService adjustments;
  @Autowired RemittanceService remittances;
  @Autowired TransactionQueryService queries;
  @Autowired JdbcTemplate jdbc;

  record Owner(CustomerService.View customer, WalletService.View wallet) {}

  Owner owner(boolean agent, String currency) {
    var c = customers.create(CustomerType.INDIVIDUAL, IdentityIT.individual());
    if (agent) c = customers.agent(c.customerNumber(), AgentType.STANDARD);
    return new Owner(c, wallets.create(new WalletService.Create(c.id(), currency, "Test wallet")));
  }

  String key() {
    return UUID.randomUUID().toString();
  }

  BigDecimal amount(String n) {
    return new BigDecimal(n);
  }

  void fund(Owner o, String value) {
    transfers.deposit(key(), new TransferService.Cash(o.wallet().walletNumber(), amount(value)));
  }

  String product(RewardMode mode) {
    var original = products.findByCode("ZETDC_USD").orElseThrow();
    return productAdmin
        .create(
            new BillerProductService.Request(
                original.getBillerId(),
                "TEST_" + key(),
                "Test product",
                original.getCurrencyId(),
                original.getSettlementWalletId(),
                mode,
                RewardType.PERCENTAGE,
                amount("2"),
                null,
                null,
                Status.ACTIVE))
        .code();
  }

  Fee fee(String type, UUID product, String value) {
    var f = new Fee();
    f.setTransactionTypeId(types.findByCode(type).orElseThrow().getId());
    f.setBillerProductId(product);
    f.setCurrencyId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    f.setCalculationType(CalculationType.FIXED);
    f.setFixedAmount(amount(value));
    f.setEffectiveFrom(Instant.now().minusSeconds(10));
    f.setPriority(100);
    f.setStatus(Status.ACTIVE);
    return feeRepository.save(f);
  }

  void disable(Fee f) {
    f.setStatus(Status.INACTIVE);
    feeRepository.save(f);
  }

  @ParameterizedTest
  @EnumSource(
      value = RewardMode.class,
      names = {"DISCOUNT", "CASHBACK"})
  void agentRewardAndExactReversal(RewardMode mode) {
    var o = owner(true, "USD");
    fund(o, "100");
    String code = mode == RewardMode.DISCOUNT ? "ZETDC_USD" : product(mode);
    var request =
        new BillPaymentService.Request(o.wallet().walletNumber(), code, "123456789", amount("20"));
    String k = key();
    var paid = bills.pay(k, request);
    assertThat(paid.commissionAmount()).isEqualByComparingTo("0.40");
    assertThat(wallets.get(o.wallet().walletNumber()).balance()).isEqualByComparingTo("80.40");
    assertThat(paid.entries()).hasSize(2);
    assertThat(paid.entries().get(0).amount())
        .isEqualByComparingTo(mode == RewardMode.DISCOUNT ? "19.60" : "20.00");
    assertThat(paid.entries().get(1).amount()).isEqualByComparingTo("0.40");
    assertThat(bills.pay(k, request)).isEqualTo(paid);
    assertThat(commissions.report(o.customer().customerNumber(), 0, 50)).hasSize(1);
    assertThat(commissions.summary(o.customer().customerNumber()).get(0).totalCommission())
        .isEqualByComparingTo("0.40");
    var reversed =
        reversals.reverse(
            paid.transactionReference(), key(), new ReversalService.Request("Test reversal"));
    assertThat(reversed.entries()).hasSize(2);
    assertThat(wallets.get(o.wallet().walletNumber()).balance()).isEqualByComparingTo("100");
    for (int i = 0; i < 2; i++) {
      assertThat(reversed.entries().get(i).debitWalletId())
          .isEqualTo(paid.entries().get(i).creditWalletId());
      assertThat(reversed.entries().get(i).creditWalletId())
          .isEqualTo(paid.entries().get(i).debitWalletId());
      assertThat(reversed.entries().get(i).amount())
          .isEqualByComparingTo(paid.entries().get(i).amount());
    }
    assertThat(commissions.summary(o.customer().customerNumber())).isEmpty();
    assertThat(queries.get(paid.transactionReference()).status())
        .isEqualTo(TransactionStatus.REVERSED);
    assertThat(ledger.findByTransactionReferenceOrderBySequenceNumber(paid.transactionReference()))
        .allMatch(l -> l.getStatus() == TransactionStatus.SUCCESS);
    assertThatThrownBy(
            () ->
                reversals.reverse(
                    paid.transactionReference(), key(), new ReversalService.Request("Again")))
        .hasMessage("TRANSACTION_ALREADY_REVERSED");
  }

  @Test
  void normalBillWithFeeAndReversal() {
    var o = owner(false, "USD");
    fund(o, "100");
    var code = product(RewardMode.NONE);
    var f = fee("BILL_PAYMENT", products.findByCode(code).orElseThrow().getId(), "0.50");
    try {
      var paid =
          bills.pay(
              key(),
              new BillPaymentService.Request(o.wallet().walletNumber(), code, "123", amount("20")));
      assertThat(paid.entries()).hasSize(2);
      assertThat(paid.feeAmount()).isEqualByComparingTo("0.50");
      assertThat(wallets.get(o.wallet().walletNumber()).balance()).isEqualByComparingTo("79.50");
      reversals.reverse(paid.transactionReference(), key(), new ReversalService.Request("Undo"));
      assertThat(wallets.get(o.wallet().walletNumber()).balance()).isEqualByComparingTo("100");
    } finally {
      disable(f);
    }
  }

  @Test
  void withdrawalAndSendFeesAreAtomic() {
    var a = owner(false, "USD");
    var b = owner(false, "USD");
    fund(a, "100");
    for (var type : List.of("WITHDRAWAL", "SEND_MONEY")) {
      var f = fee(type, null, "1");
      try {
        var result =
            type.equals("WITHDRAWAL")
                ? transfers.withdraw(
                    key(), new TransferService.Cash(a.wallet().walletNumber(), amount("10")))
                : transfers.send(
                    key(),
                    new TransferService.Send(
                        a.wallet().walletNumber(), b.wallet().walletNumber(), amount("10")));
        assertThat(result.entries()).hasSize(2);
        assertThat(result.feeAmount()).isEqualByComparingTo("1");
      } finally {
        disable(f);
      }
    }
    assertThat(wallets.get(a.wallet().walletNumber()).balance()).isEqualByComparingTo("78");
  }

  @Test
  void insufficientFeeRollsBackPrincipal() {
    var a = owner(false, "USD");
    fund(a, "10");
    var f = fee("WITHDRAWAL", null, "1");
    try {
      assertThatThrownBy(
              () ->
                  transfers.withdraw(
                      key(), new TransferService.Cash(a.wallet().walletNumber(), amount("10"))))
          .hasMessage("INSUFFICIENT_FUNDS");
      assertThat(wallets.get(a.wallet().walletNumber()).balance()).isEqualByComparingTo("10");
      assertThat(queries.walletHistory(a.wallet().walletNumber(), 0, 50)).hasSize(1);
    } finally {
      disable(f);
    }
  }

  @Test
  void failedAndPendingProviderDoNotPostTwice() {
    var a = owner(false, "USD");
    fund(a, "100");
    var fail =
        bills.pay(
            key(),
            new BillPaymentService.Request(
                a.wallet().walletNumber(), "ZETDC_USD", "FAIL", amount("20")));
    assertThat(fail.status()).isEqualTo(TransactionStatus.FAILED);
    assertThat(fail.entries()).isEmpty();
    var pending =
        bills.pay(
            key(),
            new BillPaymentService.Request(
                a.wallet().walletNumber(), "ZETDC_USD", "PENDING", amount("20")));
    assertThat(pending.status()).isEqualTo(TransactionStatus.PENDING);
    assertThat(wallets.get(a.wallet().walletNumber()).balance()).isEqualByComparingTo("100");
    var done = bills.enquire(pending.transactionReference());
    assertThat(done.status()).isEqualTo(TransactionStatus.SUCCESS);
    bills.enquire(pending.transactionReference());
    assertThat(wallets.get(a.wallet().walletNumber()).balance()).isEqualByComparingTo("80");
  }

  @Test
  void adjustmentsAreAudited() {
    var a = owner(false, "USD");
    adjustments.adjust(
        key(),
        new AdjustmentService.Request(
            a.wallet().walletNumber(), amount("20"), Direction.CREDIT, "Opening correction"));
    adjustments.adjust(
        key(),
        new AdjustmentService.Request(
            a.wallet().walletNumber(), amount("5"), Direction.DEBIT, "Correction"));
    assertThat(wallets.get(a.wallet().walletNumber()).balance()).isEqualByComparingTo("15");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_logs where action='ACCOUNT_ADJUSTMENT' and entity_id=?",
                Long.class,
                a.wallet().id().toString()))
        .isEqualTo(2L);
  }

  RemittanceService.Send sendRequest(Owner a, Owner b) {
    return new RemittanceService.Send(
        "USD",
        "USD",
        amount("20"),
        null,
        "Sender",
        "123",
        "Receiver",
        "456",
        "ID1",
        "Family support",
        "SALARY",
        null);
  }

  @Test
  void remittanceSendFeePayoutAndLifecycleReversals() {
    var a = owner(false, "USD");
    var b = owner(false, "USD");
    fund(a, "100");
    var f = fee("REMITTANCE_SEND", null, "3");
    try {
      var sent = remittances.send(key(), sendRequest(a, b));
      assertThat(sent.entries()).hasSize(2);
      assertThat(wallets.get(a.wallet().walletNumber()).balance()).isEqualByComparingTo("100");
      var details = remittances.get(sent.remittanceReference());
      var paid =
          remittances.payout(
              sent.remittanceReference(),
              key(),
              new RemittanceService.Payout("456", details.collectionCode()));
      assertThat(wallets.get(b.wallet().walletNumber()).balance()).isZero();
      assertThatThrownBy(
              () ->
                  remittances.payout(
                      sent.remittanceReference(),
                      key(),
                      new RemittanceService.Payout("456", details.collectionCode())))
          .hasMessage("REMITTANCE_ALREADY_PAID");
      assertThatThrownBy(
              () ->
                  reversals.reverse(
                      sent.transactionReference(), key(), new ReversalService.Request("Too late")))
          .hasMessage("TRANSACTION_NOT_REVERSIBLE");
      reversals.reverse(
          paid.transactionReference(), key(), new ReversalService.Request("Undo payout"));
      reversals.reverse(
          sent.transactionReference(), key(), new ReversalService.Request("Undo send"));
      assertThat(wallets.get(a.wallet().walletNumber()).balance()).isEqualByComparingTo("100");
      assertThat(remittances.get(sent.remittanceReference()).status())
          .isEqualTo(RemittanceStatus.REVERSED);
    } finally {
      disable(f);
    }
  }

  @Test
  void rejectCurrencyMismatchAndFractionalCent() {
    var a = owner(false, "USD");
    var b = owner(false, "ZWG");
    fund(a, "100");
    assertThatThrownBy(
            () ->
                transfers.send(
                    key(),
                    new TransferService.Send(
                        a.wallet().walletNumber(), b.wallet().walletNumber(), amount("1"))))
        .hasMessage("INVALID_CURRENCY");
    assertThatThrownBy(
            () ->
                transfers.withdraw(
                    key(), new TransferService.Cash(a.wallet().walletNumber(), amount("1.001"))))
        .hasMessageContaining("precision");
  }

  @Test
  void databaseRejectsLedgerMutations() {
    var a = owner(false, "USD");
    fund(a, "10");
    var id =
        queries
            .walletHistory(a.wallet().walletNumber(), 0, 50)
            .get(0)
            .entries()
            .get(0)
            .id()
            .toString();
    assertThatThrownBy(() -> jdbc.update("UPDATE transactions_ledger SET amount=1 WHERE id=?", id))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThatThrownBy(() -> jdbc.update("DELETE FROM transactions_ledger WHERE id=?", id))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }

  @Test
  void crossCurrencyPayoutRequiresPrefundedSettlement() {
    var a = owner(false, "USD");
    var b = owner(false, "BWP");
    fund(a, "100");
    jdbc.update("update currencies set rate_against_usd=13.5 where code='BWP'");
    var sent =
        remittances.send(
            key(),
            new RemittanceService.Send(
                "USD",
                "BWP",
                amount("100"),
                null,
                "Sender",
                "123",
                "Receiver",
                "456",
                "ID1",
                "Family support",
                "SALARY",
                null));
    assertThat(remittances.get(sent.remittanceReference()).payoutAmount())
        .isEqualByComparingTo("1350");
    assertThatThrownBy(
            () ->
                remittances.payout(
                    sent.remittanceReference(),
                    key(),
                    new RemittanceService.Payout(
                        "456", remittances.get(sent.remittanceReference()).collectionCode())))
        .hasMessage("INSUFFICIENT_FUNDS");
    adjustments.adjust(
        key(),
        new AdjustmentService.Request(
            "REMITTANCE_SETTLEMENT_BWP",
            amount("1350"),
            Direction.CREDIT,
            "Documented settlement prefunding"));
    remittances.payout(
        sent.remittanceReference(),
        key(),
        new RemittanceService.Payout(
            "456", remittances.get(sent.remittanceReference()).collectionCode()));
    assertThat(wallets.get(b.wallet().walletNumber()).balance()).isZero();
  }

  @Test
  void reversalRetryIsIdempotent() {
    var a = owner(false, "USD");
    var deposited =
        transfers.deposit(key(), new TransferService.Cash(a.wallet().walletNumber(), amount("20")));
    var k = key();
    var request = new ReversalService.Request("Undo");
    var reversed = reversals.reverse(deposited.transactionReference(), k, request);
    assertThat(reversals.reverse(deposited.transactionReference(), k, request)).isEqualTo(reversed);
    assertThat(wallets.get(a.wallet().walletNumber()).balance()).isZero();
  }

  @Test
  void pendingPurchasePreservesQuotedFee() {
    var a = owner(false, "USD");
    fund(a, "100");
    var code = product(RewardMode.NONE);
    var f = fee("BILL_PAYMENT", products.findByCode(code).orElseThrow().getId(), "0.50");
    try {
      var pending =
          bills.pay(
              key(),
              new BillPaymentService.Request(
                  a.wallet().walletNumber(), code, "PENDING", amount("20")));
      f.setFixedAmount(amount("2"));
      feeRepository.save(f);
      var result = bills.enquire(pending.transactionReference());
      assertThat(result.feeAmount()).isEqualByComparingTo("0.50");
      assertThat(wallets.get(a.wallet().walletNumber()).balance()).isEqualByComparingTo("79.50");
    } finally {
      disable(f);
    }
  }
}
