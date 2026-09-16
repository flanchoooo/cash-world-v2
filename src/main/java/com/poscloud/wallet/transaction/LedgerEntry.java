package com.poscloud.wallet.transaction;

import com.poscloud.wallet.common.BaseEntity;
import com.poscloud.wallet.common.Types.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@org.hibernate.annotations.Immutable
@Entity
@Table(name = "transactions_ledger")
public class LedgerEntry extends BaseEntity {
  private String transactionReference;
  private UUID transactionTypeId;

  @Enumerated(EnumType.STRING)
  private EntryType entryType;

  private UUID debitWalletId;
  private UUID creditWalletId;

  @Column(precision = 19, scale = 4)
  private BigDecimal amount;

  private UUID currencyId;
  private UUID customerId;
  private UUID agentCustomerId;
  private UUID billerId;
  private UUID billerProductId;

  @Column(precision = 19, scale = 4)
  private BigDecimal faceValue;

  @Column(precision = 19, scale = 4)
  private BigDecimal feeAmount;

  @Column(precision = 19, scale = 4)
  private BigDecimal commissionAmount;

  @Enumerated(EnumType.STRING)
  private RewardMode rewardMode;

  private String externalReference;
  private String providerReference;
  private String originalTransactionReference;

  @Enumerated(EnumType.STRING)
  private TransactionStatus status;

  private String narration;
  private String idempotencyKey;
  private Instant completedAt;

  @Column(insertable = false, updatable = false)
  private long sequenceNumber;
}
