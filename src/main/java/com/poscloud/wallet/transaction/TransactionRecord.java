package com.poscloud.wallet.transaction;

import com.poscloud.wallet.common.BaseEntity;
import com.poscloud.wallet.common.Types.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "transaction_requests")
public class TransactionRecord extends BaseEntity {
  @Column(length = 50, nullable = false, unique = true)
  private String transactionReference;
  private UUID userId;
  private String idempotencyKey;
  private String requestHash;
  private String operation;

  @Enumerated(EnumType.STRING)
  private TransactionStatus status;

  @Column(length = 50, unique = true)
  private String originalTransactionReference;

  @Column(length = 50)
  private String remittanceReference;

  @Column(length = 3)
  private String sourceCurrency;

  @Column(length = 3)
  private String destinationCurrency;

  @Column(precision = 19, scale = 4)
  private BigDecimal sourceAmount;

  @Column(precision = 19, scale = 4)
  private BigDecimal sourceFeeAmount;

  @Column(precision = 19, scale = 4)
  private BigDecimal destinationFeeAmount;

  @Column(precision = 19, scale = 6)
  private BigDecimal exchangeRate;

  @Column(precision = 19, scale = 4)
  private BigDecimal recipientAmount;

  @Column(precision = 19, scale = 4)
  private BigDecimal totalSourceAmount;

  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
  private String resultData;
}
