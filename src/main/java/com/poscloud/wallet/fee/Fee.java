package com.poscloud.wallet.fee;

import com.poscloud.wallet.common.BaseEntity;
import com.poscloud.wallet.common.Types.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "fees")
public class Fee extends BaseEntity {
  private UUID transactionTypeId;
  private UUID billerProductId;

  @Enumerated(EnumType.STRING)
  private CustomerType customerType;

  private Boolean agentOnly;
  private UUID currencyId;

  @Enumerated(EnumType.STRING)
  private CalculationType calculationType;

  @Column(precision = 19, scale = 4)
  private BigDecimal fixedAmount;

  @Column(precision = 10, scale = 4)
  private BigDecimal percentage;

  @Column(precision = 19, scale = 4)
  private BigDecimal minimumFee;

  @Column(precision = 19, scale = 4)
  private BigDecimal maximumFee;

  @Column(precision = 19, scale = 4)
  private BigDecimal minTransactionAmount;

  @Column(precision = 19, scale = 4)
  private BigDecimal maxTransactionAmount;

  private Instant effectiveFrom;
  private Instant effectiveTo;
  private int priority;

  @Enumerated(EnumType.STRING)
  private Status status;
}
