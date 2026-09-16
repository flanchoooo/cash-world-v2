package com.poscloud.wallet.currency;

import com.poscloud.wallet.common.BaseEntity;
import com.poscloud.wallet.common.Types.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "currencies")
public class Currency extends BaseEntity {
  private String code;
  private String name;
  private String symbol;
  private int decimalPlaces;

  @Column(precision = 20, scale = 10, nullable = false)
  private BigDecimal rateAgainstUsd;

  @Enumerated(EnumType.STRING)
  private Status status;
}
