package com.poscloud.wallet;

import static org.assertj.core.api.Assertions.*;

import com.poscloud.wallet.auth.AuthService;
import com.poscloud.wallet.common.Types.*;
import com.poscloud.wallet.customer.CustomerService;
import com.poscloud.wallet.transaction.*;
import com.poscloud.wallet.wallet.WalletService;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class ConcurrencyIT extends MySqlITSupport {
  @Autowired CustomerService customers;
  @Autowired WalletService wallets;
  @Autowired TransferService transfers;
  @Autowired ReversalService reversals;
  @Autowired AuthService auth;
  @Autowired com.poscloud.wallet.remittance.RemittanceService remittances;
  @Autowired TransactionQueryService queries;

  String wallet() {
    var c = customers.create(CustomerType.INDIVIDUAL, IdentityIT.individual());
    return wallets.create(new WalletService.Create(c.id(), "USD", "Concurrent")).walletNumber();
  }

  String key() {
    return UUID.randomUUID().toString();
  }

  UUID actor() {
    return auth.register(
            new AuthService.Register(
                "ops-" + key(), "long-test-password", null, Role.OPERATIONS, null, null))
        .id();
  }

  List<Object> race(UUID actor1, UUID actor2, Supplier<?> one, Supplier<?> two) throws Exception {
    var pool = Executors.newFixedThreadPool(2);
    var start = new CountDownLatch(1);
    try {
      var f1 = pool.submit(() -> invoke(actor1, start, one));
      var f2 = pool.submit(() -> invoke(actor2, start, two));
      start.countDown();
      return List.of(f1.get(30, TimeUnit.SECONDS), f2.get(30, TimeUnit.SECONDS));
    } finally {
      pool.shutdownNow();
    }
  }

  Object invoke(UUID actor, CountDownLatch start, Supplier<?> action) throws Exception {
    start.await();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                actor.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_OPERATIONS"))));
    try {
      return action.get();
    } catch (RuntimeException e) {
      return e;
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void twoUsersCannotDoubleSpendSameWallet() throws Exception {
    var w = wallet();
    transfers.deposit(key(), new TransferService.Cash(w, new BigDecimal("100")));
    var a = actor();
    var b = actor();
    var results =
        race(
            a,
            b,
            () -> transfers.withdraw(key(), new TransferService.Cash(w, new BigDecimal("80"))),
            () -> transfers.withdraw(key(), new TransferService.Cash(w, new BigDecimal("80"))));
    assertThat(results.stream().filter(TransactionResult.class::isInstance).count()).isEqualTo(1);
    assertThat(wallets.get(w).balance()).isEqualByComparingTo("20");
    assertThat(
            results.stream()
                .filter(Throwable.class::isInstance)
                .map(x -> ((Throwable) x).getMessage())
                .toList())
        .containsExactly("INSUFFICIENT_FUNDS");
  }

  @Test
  void concurrentSameKeyReturnsSameResult() throws Exception {
    var w = wallet();
    var a = actor();
    var k = key();
    var request = new TransferService.Cash(w, new BigDecimal("100"));
    var results =
        race(a, a, () -> transfers.deposit(k, request), () -> transfers.deposit(k, request));
    assertThat(results).allMatch(TransactionResult.class::isInstance);
    assertThat(results.get(0)).isEqualTo(results.get(1));
    assertThat(wallets.get(w).balance()).isEqualByComparingTo("100");
  }

  @Test
  void concurrentReversalPostsOnlyOnce() throws Exception {
    var w = wallet();
    var original = transfers.deposit(key(), new TransferService.Cash(w, new BigDecimal("100")));
    var a = actor();
    var b = actor();
    var results =
        race(
            a,
            b,
            () ->
                reversals.reverse(
                    original.transactionReference(), key(), new ReversalService.Request("First")),
            () ->
                reversals.reverse(
                    original.transactionReference(), key(), new ReversalService.Request("Second")));
    assertThat(results.stream().filter(TransactionResult.class::isInstance).count()).isEqualTo(1);
    assertThat(wallets.get(w).balance()).isZero();
    assertThat(queries.walletHistory(w, 0, 50)).hasSize(2);
  }

  @Test
  void oppositeTransfersUseConsistentWalletLockOrder() throws Exception {
    var a = wallet();
    var b = wallet();
    transfers.deposit(key(), new TransferService.Cash(a, new BigDecimal("100")));
    transfers.deposit(key(), new TransferService.Cash(b, new BigDecimal("100")));
    var results =
        race(
            actor(),
            actor(),
            () -> transfers.send(key(), new TransferService.Send(a, b, BigDecimal.TEN)),
            () -> transfers.send(key(), new TransferService.Send(b, a, BigDecimal.TEN)));
    assertThat(results).allMatch(TransactionResult.class::isInstance);
    assertThat(wallets.get(a).balance()).isEqualByComparingTo("100");
    assertThat(wallets.get(b).balance()).isEqualByComparingTo("100");
  }

  @Test
  void concurrentPayoutPostsOnlyOnce() throws Exception {
    var source = wallet();
    var target = wallet();
    transfers.deposit(key(), new TransferService.Cash(source, new BigDecimal("100")));
    var receiver = wallets.get(target).customerId();
    var sent =
        remittances.send(
            key(),
            new com.poscloud.wallet.remittance.RemittanceService.Send(
                "USD",
                "USD",
                new BigDecimal("20"),
                null,
                "Sender",
                "123",
                "Receiver",
                "456",
                "ID1",
                "Family support",
                "SALARY",
                null));
    var request =
        new com.poscloud.wallet.remittance.RemittanceService.Payout(
            "456", remittances.get(sent.remittanceReference()).collectionCode());
    var results =
        race(
            actor(),
            actor(),
            () -> remittances.payout(sent.remittanceReference(), key(), request),
            () -> remittances.payout(sent.remittanceReference(), key(), request));
    assertThat(results.stream().filter(TransactionResult.class::isInstance).count()).isEqualTo(1);
    assertThat(wallets.get(target).balance()).isZero();
  }
}
