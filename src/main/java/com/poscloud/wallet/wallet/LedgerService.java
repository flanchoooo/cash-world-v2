package com.poscloud.wallet.wallet;

import com.poscloud.wallet.common.*;
import com.poscloud.wallet.common.Types.*;
import com.poscloud.wallet.transaction.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/**
 * The sole balance writer. Locks the entire posting set in UUID order before changing any balance.
 */
@Service
@RequiredArgsConstructor
public class LedgerService {
  private final WalletRepository wallets;
  private final WalletService walletService;
  private final TransactionTypeRepository types;
  private final EntityManager entityManager;

  // Service lookups may have loaded a stale @Version before this transaction waited.
  // Detach only that unmodified wallet, then read its current version with FOR UPDATE.
  private Wallet lockFresh(UUID id) {
    var cached = entityManager.find(Wallet.class, id);
    if (cached == null) throw new ApiException("WALLET_NOT_FOUND");
    entityManager.detach(cached);
    return wallets.lock(id).orElseThrow(() -> new ApiException("WALLET_NOT_FOUND"));
  }

  @io.swagger.v3.oas.annotations.media.Schema(name = "LedgerServiceMetadata")
  public record Metadata(
      UUID customerId,
      UUID agentCustomerId,
      UUID billerId,
      UUID productId,
      BigDecimal faceValue,
      BigDecimal feeAmount,
      BigDecimal commissionAmount,
      RewardMode rewardMode,
      String providerReference,
      String originalReference,
      String narration,
      String idempotencyKey) {
    public static Metadata basic(UUID customerId, BigDecimal amount, String narration) {
      return new Metadata(
          customerId,
          null,
          null,
          null,
          amount,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          RewardMode.NONE,
          null,
          null,
          narration,
          null);
    }
  }

  @io.swagger.v3.oas.annotations.media.Schema(name = "LedgerServicePosting")
  public record Posting(
      UUID debit,
      UUID credit,
      BigDecimal amount,
      UUID currency,
      EntryType entryType,
      Metadata metadata) {}

  @Transactional(propagation = Propagation.MANDATORY)
  public void checkBatch(List<Posting> postings) {
    var ids = new TreeSet<UUID>(Comparator.comparing(UUID::toString));
    postings.forEach(
        p -> {
          ids.add(p.debit());
          ids.add(p.credit());
        });
    var locked = new HashMap<UUID, Wallet>();
    var balances = new HashMap<UUID, BigDecimal>();
    for (var id : ids) {
      var w = lockFresh(id);
      walletService.usable(w);
      locked.put(id, w);
      balances.put(id, w.getBalance());
    }
    for (var p : postings) {
      ApiException.require(!p.debit().equals(p.credit()), "SAME_WALLET");
      var d = locked.get(p.debit());
      var c = locked.get(p.credit());
      ApiException.require(
          d.getCurrencyId().equals(p.currency()) && c.getCurrencyId().equals(p.currency()),
          "INVALID_CURRENCY");
      Money.amount(p.amount(), walletService.currency(p.currency()).getDecimalPlaces());
      var next = balances.get(p.debit()).subtract(p.amount());
      ApiException.require(d.isAllowNegativeBalance() || next.signum() >= 0, "INSUFFICIENT_FUNDS");
      ApiException.require(
          next.abs().compareTo(Money.MAX) <= 0
              && balances.get(p.credit()).add(p.amount()).compareTo(Money.MAX) <= 0,
          "BALANCE_LIMIT_EXCEEDED");
      balances.put(p.debit(), next);
      balances.put(p.credit(), balances.get(p.credit()).add(p.amount()));
    }
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void postEntry(
      String ref,
      String type,
      UUID debit,
      UUID credit,
      BigDecimal amount,
      UUID currency,
      EntryType entryType,
      Metadata metadata) {
    postBatch(
        ref, type, List.of(new Posting(debit, credit, amount, currency, entryType, metadata)));
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void postBatch(String ref, String type, List<Posting> postings) {
    ApiException.require(!postings.isEmpty(), "INVALID_AMOUNT");
    var transactionType =
        types.findByCode(type).orElseThrow(() -> new ApiException("TRANSACTION_TYPE_NOT_FOUND"));
    ApiException.require(
        transactionType.getStatus() == Status.ACTIVE, "TRANSACTION_TYPE_NOT_FOUND");
    var ids = new TreeSet<UUID>(Comparator.comparing(UUID::toString));
    postings.forEach(
        p -> {
          ids.add(p.debit());
          ids.add(p.credit());
        });
    var locked = new HashMap<UUID, Wallet>();
    for (var id : ids) {
      var w = lockFresh(id);
      walletService.usable(w);
      locked.put(id, w);
    }
    for (var p : postings) {
      ApiException.require(
          p.entryType() != EntryType.FEE || transactionType.isAllowsFee(), "FEE_NOT_ALLOWED");
      ApiException.require(
          p.entryType() != EntryType.COMMISSION || transactionType.isAllowsCommission(),
          "COMMISSION_NOT_ALLOWED");
      ApiException.require(!p.debit().equals(p.credit()), "SAME_WALLET");
      var debit = locked.get(p.debit());
      var credit = locked.get(p.credit());
      ApiException.require(
          debit.getCurrencyId().equals(p.currency()) && credit.getCurrencyId().equals(p.currency()),
          "INVALID_CURRENCY");
      var amount =
          Money.amount(p.amount(), walletService.currency(p.currency()).getDecimalPlaces());
      var balance = debit.getBalance().subtract(amount);
      ApiException.require(
          debit.isAllowNegativeBalance() || balance.signum() >= 0, "INSUFFICIENT_FUNDS");
      ApiException.require(
          balance.abs().compareTo(Money.MAX) <= 0
              && credit.getBalance().add(amount).compareTo(Money.MAX) <= 0,
          "BALANCE_LIMIT_EXCEEDED");
      debit.applyBalance(balance);
      credit.applyBalance(credit.getBalance().add(amount));
      var m = p.metadata();
      var row =
          LedgerEntry.builder()
              .transactionReference(ref)
              .transactionTypeId(transactionType.getId())
              .entryType(p.entryType())
              .debitWalletId(p.debit())
              .creditWalletId(p.credit())
              .amount(amount)
              .currencyId(p.currency())
              .customerId(m.customerId())
              .agentCustomerId(m.agentCustomerId())
              .billerId(m.billerId())
              .billerProductId(m.productId())
              .faceValue(m.faceValue())
              .feeAmount(m.feeAmount())
              .commissionAmount(m.commissionAmount())
              .rewardMode(m.rewardMode())
              .providerReference(m.providerReference())
              .originalTransactionReference(m.originalReference())
              .narration(m.narration())
              .idempotencyKey(m.idempotencyKey())
              .status(TransactionStatus.SUCCESS)
              .completedAt(Instant.now())
              .build();
      entityManager.persist(row);
    }
    entityManager.flush();
  }
}
