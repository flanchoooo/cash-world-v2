package com.poscloud.wallet.transaction;

import com.poscloud.wallet.auth.AccessService;
import com.poscloud.wallet.common.*;
import com.poscloud.wallet.common.Types.*;
import com.poscloud.wallet.wallet.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransferService {
  private final WalletService wallets;
  private final LedgerService ledger;
  private final IdempotencyService idempotency;
  private final TransactionQueryService queries;
  private final AccessService access;
  private final com.poscloud.wallet.fee.FeeService fees;
  private final com.poscloud.wallet.customer.CustomerRepository customers;

  @io.swagger.v3.oas.annotations.media.Schema(name = "TransferServiceCash")
  public record Cash(
      @NotBlank String walletNumber,
      @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4)
          BigDecimal amount) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "TransferServiceSend")
  public record Send(
      @NotBlank String sourceWalletNumber,
      @NotBlank String destinationWalletNumber,
      @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4)
          BigDecimal amount) {}

  public TransactionResult deposit(String key, Cash r) {
    access.requireStaff();
    access.requirePermission(Permission.WALLET_DEPOSIT);
    return idempotency.execute(
        key,
        "DEPOSIT",
        r,
        t -> {
          var w = wallets.find(r.walletNumber());
          return post(
              t,
              "DEPOSIT",
              wallets.system("CASH_SETTLEMENT", w.getCurrencyId()),
              w,
              r.amount(),
              w.getCustomerId());
        });
  }

  public TransactionResult withdraw(String key, Cash r) {
    if (access.staff()) access.requirePermission(Permission.WALLET_WITHDRAW);
    return idempotency.execute(
        key,
        "WITHDRAWAL",
        r,
        t -> {
          var w = wallets.find(r.walletNumber());
          wallets.owned(w);
          return post(
              t,
              "WITHDRAWAL",
              w,
              wallets.system("CASH_SETTLEMENT", w.getCurrencyId()),
              r.amount(),
              w.getCustomerId());
        });
  }

  public TransactionResult send(String key, Send r) {
    if (access.staff()) access.requirePermission(Permission.WALLET_SEND);
    return idempotency.execute(
        key,
        "SEND_MONEY",
        r,
        t -> {
          var source = wallets.find(r.sourceWalletNumber());
          wallets.owned(source);
          var destination = wallets.find(r.destinationWalletNumber());
          ApiException.require(
              source.getWalletType() == WalletType.CUSTOMER
                  && destination.getWalletType() == WalletType.CUSTOMER,
              "INVALID_WALLET_TYPE");
          return post(t, "SEND_MONEY", source, destination, r.amount(), source.getCustomerId());
        });
  }

  private TransactionResult post(
      TransactionRecord t,
      String type,
      Wallet source,
      Wallet destination,
      BigDecimal amount,
      UUID customer) {
    var currency = wallets.currency(source.getCurrencyId());
    Money.amount(amount, currency.getDecimalPlaces());
    var owner = customer == null ? null : customers.findById(customer).orElseThrow();
    var fee = fees.calculateFee(type, null, owner, currency, amount);
    var m =
        new LedgerService.Metadata(
            customer,
            null,
            null,
            null,
            amount,
            fee,
            BigDecimal.ZERO,
            RewardMode.NONE,
            null,
            null,
            type,
            t.getIdempotencyKey());
    var postings = new ArrayList<LedgerService.Posting>();
    postings.add(
        new LedgerService.Posting(
            source.getId(),
            destination.getId(),
            amount,
            source.getCurrencyId(),
            EntryType.PRINCIPAL,
            m));
    if (fee.signum() > 0) {
      var payer = type.equals("DEPOSIT") ? destination : source;
      postings.add(
          new LedgerService.Posting(
              payer.getId(),
              wallets.system("FEE_REVENUE", source.getCurrencyId()).getId(),
              fee,
              source.getCurrencyId(),
              EntryType.FEE,
              m));
    }
    ledger.postBatch(t.getTransactionReference(), type, postings);
    return queries.result(t.getTransactionReference(), null);
  }
}
