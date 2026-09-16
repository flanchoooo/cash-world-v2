package com.poscloud.wallet.biller;

import com.poscloud.wallet.common.BaseEntity;
import com.poscloud.wallet.common.Types.Status;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "customer_product_allocations")
public class CustomerProductAllocation extends BaseEntity {
  private UUID customerId;
  private UUID commissionPlanId;
  private UUID billerProductId;
  private String arrangementName;

  @Column(precision = 9, scale = 4)
  private BigDecimal totalCommissionPercentage;

  @Column(precision = 9, scale = 4)
  private BigDecimal agentCommissionPercentage;

  @Column(precision = 9, scale = 4)
  private BigDecimal platformCommissionPercentage;

  @Enumerated(EnumType.STRING)
  private Status status;
}
