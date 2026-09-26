package com.poscloud.wallet.transaction;

import com.poscloud.wallet.common.Types.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@io.swagger.v3.oas.annotations.media.Schema(name = "TransactionResult")
public record TransactionResult(
    String transactionReference,
    String transactionType,
    TransactionStatus status,
    BigDecimal faceValue,
    BigDecimal feeAmount,
    BigDecimal commissionAmount,
    String currency,
    UUID biller,
    UUID product,
    Instant createdAt,
    Instant completedAt,
    String remittanceReference,
    List<Entry> entries,
    BigDecimal platformCommissionAmount) {
  public TransactionResult(String transactionReference, String transactionType, TransactionStatus status,
      BigDecimal faceValue, BigDecimal feeAmount, BigDecimal commissionAmount, String currency,
      UUID biller, UUID product, Instant createdAt, Instant completedAt, String remittanceReference,
      List<Entry> entries) {
    this(transactionReference, transactionType, status, faceValue, feeAmount, commissionAmount, currency,
        biller, product, createdAt, completedAt, remittanceReference, entries, BigDecimal.ZERO);
  }

  public TransactionResult {
    if (platformCommissionAmount == null) platformCommissionAmount = BigDecimal.ZERO;
  }
  @io.swagger.v3.oas.annotations.media.Schema(name = "TransactionResultEntry")
  public record Entry(
      UUID id, UUID debitWalletId, UUID creditWalletId, BigDecimal amount, EntryType entryType) {}
}
