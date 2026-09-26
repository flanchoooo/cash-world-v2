package com.poscloud.wallet.transaction;

import com.poscloud.wallet.auth.AccessService;
import com.poscloud.wallet.common.*;
import com.poscloud.wallet.common.Types.*;
import com.poscloud.wallet.currency.CurrencyRepository;
import com.poscloud.wallet.customer.CustomerService;
import com.poscloud.wallet.wallet.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionQueryService {
  private final LedgerEntryRepository ledger;
  private final TransactionTypeRepository types;
  private final CurrencyRepository currencies;
  private final TransactionRecordRepository records;
  private final IdempotencyService idempotency;
  private final WalletService wallets;
  private final CustomerService customers;
  private final AccessService access;

  public TransactionResult result(String ref, String remittance) {
    var rows = ledger.findByTransactionReferenceOrderBySequenceNumber(ref);
    ApiException.require(!rows.isEmpty(), "TRANSACTION_NOT_FOUND");
    var first = rows.get(0);
    var summary = rows.stream().filter(row -> row.getFaceValue() != null).findFirst().orElse(first);
    var fee = rows.stream().filter(row -> row.getEntryType() == EntryType.FEE)
        .map(LedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    if (fee.signum() == 0) fee = first.getFeeAmount();
    var status =
        records.existsByOriginalTransactionReference(ref)
            ? TransactionStatus.REVERSED
            : TransactionStatus.SUCCESS;
    return new TransactionResult(
        ref,
        types.findById(first.getTransactionTypeId()).orElseThrow().getCode(),
        status,
        summary.getFaceValue(),
        fee,
        summary.getCommissionAmount(),
        currencies.findById(first.getCurrencyId()).orElseThrow().getCode(),
        first.getBillerId(),
        first.getBillerProductId(),
        first.getCreatedAt(),
        first.getCompletedAt(),
        remittance,
        rows.stream()
            .map(
                l ->
                    new TransactionResult.Entry(
                        l.getId(),
                        l.getDebitWalletId(),
                        l.getCreditWalletId(),
                        l.getAmount(),
                        l.getEntryType()))
            .toList(),
        summary.getPlatformCommissionAmount() == null ? BigDecimal.ZERO : summary.getPlatformCommissionAmount());
  }

  public TransactionResult get(String ref) {
    if (access.staff()) access.requirePermission(Permission.TRANSACTIONS_VIEW);
    var record =
        records
            .findByTransactionReference(ref)
            .orElseThrow(() -> new ApiException("TRANSACTION_NOT_FOUND"));
    if (!access.staff() && !record.getUserId().equals(access.current().getId())) {
      var customer = access.current().getCustomerId();
      ApiException.require(
          customer != null
              && ledger.forCustomer(customer).stream()
                  .anyMatch(l -> l.getTransactionReference().equals(ref)),
          "FORBIDDEN");
    }
    var original = idempotency.deserialize(record.getResultData());
    if (original.entries().isEmpty()) return original;
    return result(ref, original.remittanceReference());
  }

  public List<TransactionResult> walletHistory(String number, int offset, int limit) {
    if (access.staff()) access.requirePermission(Permission.WALLETS_VIEW);
    var w = wallets.find(number);
    wallets.owned(w);
    return grouped(ledger.forWallet(w.getId()), offset, limit);
  }

  public List<TransactionResult> customerHistory(String number, int offset, int limit) {
    if (access.staff()) access.requirePermission(Permission.TRANSACTIONS_VIEW);
    var c = customers.find(number);
    access.customer(c.getId());
    return grouped(ledger.forCustomer(c.getId()), offset, limit);
  }

  private List<TransactionResult> grouped(List<LedgerEntry> rows, int offset, int limit) {
    ApiException.require(offset >= 0 && limit > 0 && limit <= 200, "INVALID_PAGE");
    return rows.stream()
        .map(LedgerEntry::getTransactionReference)
        .distinct()
        .skip(offset)
        .limit(limit)
        .map(r -> result(r, null))
        .toList();
  }

  @io.swagger.v3.oas.annotations.media.Schema(name = "TransactionQueryServiceStatementLine")
  public record StatementLine(
      Instant date,
      String transactionReference,
      String description,
      BigDecimal debit,
      BigDecimal credit,
      BigDecimal runningBalance) {}

  public List<StatementLine> statement(String number, int offset, int limit) {
    if (access.staff()) access.requirePermission(Permission.WALLETS_VIEW);
    ApiException.require(offset >= 0 && limit > 0 && limit <= 1000, "INVALID_PAGE");
    var w = wallets.find(number);
    wallets.owned(w);
    var balance = BigDecimal.ZERO;
    var result = new ArrayList<StatementLine>();
    int index = 0;
    for (var l : ledger.forWallet(w.getId())) {
      var debit = l.getDebitWalletId().equals(w.getId()) ? l.getAmount() : BigDecimal.ZERO;
      var credit = l.getCreditWalletId().equals(w.getId()) ? l.getAmount() : BigDecimal.ZERO;
      balance = balance.add(credit).subtract(debit);
      if (index++ >= offset && result.size() < limit)
        result.add(
            new StatementLine(
                l.getCreatedAt(),
                l.getTransactionReference(),
                l.getNarration(),
                debit,
                credit,
                balance));
    }
    return result;
  }
}
