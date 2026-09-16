package com.poscloud.wallet;

import static org.assertj.core.api.Assertions.*;

import com.poscloud.wallet.common.Types.*;
import com.poscloud.wallet.customer.CustomerService;
import com.poscloud.wallet.transaction.*;
import com.poscloud.wallet.wallet.WalletService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class WalletIT extends MySqlITSupport {
  @Autowired CustomerService customers;
  @Autowired WalletService wallets;
  @Autowired TransferService transfers;
  @Autowired TransactionQueryService queries;
  @Autowired LedgerEntryRepository ledger;

  String wallet() {
    var c = customers.create(CustomerType.INDIVIDUAL, IdentityIT.individual());
    return wallets.create(new WalletService.Create(c.id(), "USD", "Test")).walletNumber();
  }

  String key() {
    return UUID.randomUUID().toString();
  }

  @Test
  void depositWithdrawalSendAndStatement() {
    var a = wallet();
    var b = wallet();
    transfers.deposit(key(), new TransferService.Cash(a, new BigDecimal("100")));
    transfers.withdraw(key(), new TransferService.Cash(a, new BigDecimal("10")));
    var sent = transfers.send(key(), new TransferService.Send(a, b, new BigDecimal("20")));
    assertThat(wallets.get(a).balance()).isEqualByComparingTo("70");
    assertThat(wallets.get(b).balance()).isEqualByComparingTo("20");
    assertThat(queries.walletHistory(a, 0, 50)).hasSize(3);
    assertThat(queries.statement(a, 0, 100).get(2).runningBalance()).isEqualByComparingTo("70");
    assertThat(ledger.findByTransactionReferenceOrderBySequenceNumber(sent.transactionReference()))
        .hasSize(1);
  }

  @Test
  void idempotentRetryAndConflict() {
    var a = wallet();
    var k = key();
    var request = new TransferService.Cash(a, new BigDecimal("100"));
    var first = transfers.deposit(k, request);
    assertThat(transfers.deposit(k, request)).isEqualTo(first);
    assertThat(wallets.get(a).balance()).isEqualByComparingTo("100");
    assertThatThrownBy(
            () -> transfers.deposit(k, new TransferService.Cash(a, new BigDecimal("200"))))
        .hasMessage("IDEMPOTENCY_CONFLICT");
  }

  @Test
  void blockedAndInsufficient() {
    var a = wallet();
    assertThatThrownBy(() -> transfers.withdraw(key(), new TransferService.Cash(a, BigDecimal.ONE)))
        .hasMessage("INSUFFICIENT_FUNDS");
    wallets.status(a, WalletStatus.BLOCKED);
    assertThatThrownBy(() -> transfers.deposit(key(), new TransferService.Cash(a, BigDecimal.ONE)))
        .hasMessage("WALLET_BLOCKED");
    assertThat(wallets.get(a).balance()).isZero();
  }
}
