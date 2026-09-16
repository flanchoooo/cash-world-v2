package com.poscloud.wallet.biller;

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
@Table(name = "biller_products")
public class BillerProduct extends BaseEntity {
  private UUID billerId;
  private String code;
  private String name;
  private UUID currencyId;
  private UUID settlementWalletId;

  @Enumerated(EnumType.STRING)
  private RewardMode agentRewardMode;

  @Enumerated(EnumType.STRING)
  private RewardType agentRewardType;

  @Column(precision = 19, scale = 4)
  private BigDecimal agentRewardValue;

  @Column(precision = 19, scale = 4)
  private BigDecimal minimumCommission;

  @Column(precision = 19, scale = 4)
  private BigDecimal maximumCommission;

  @Enumerated(EnumType.STRING)
  private Status status;
}
