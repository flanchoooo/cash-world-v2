package com.poscloud.wallet.external;

import com.poscloud.wallet.common.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "external_sales")
public class ExternalSale extends BaseEntity {
  private String transactionReference;
  private String reversalTransactionReference;
  private UUID customerId;
  private UUID walletId;
  private UUID billerProductId;
  private String customerReference;
  @Column(precision = 19, scale = 4) private BigDecimal faceValue;
  @Column(precision = 19, scale = 4) private BigDecimal walletDebitAmount;
  @Column(precision = 19, scale = 4) private BigDecimal totalCommissionAmount;
  @Column(precision = 19, scale = 4) private BigDecimal agentCommissionAmount;
  @Column(precision = 19, scale = 4) private BigDecimal platformCommissionAmount;
  private boolean creditSale;
  private String collectingAgentName;
  private String collectingAgentMobile;
  private String collectingAgentIdNumber;
  @Column(precision = 19, scale = 4) private BigDecimal amountDue;
  private String creditStatus;
  private Instant collectedAt;
  private UUID collectedByUserId;
  private String providerReference;
  private String providerStatus;
  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON) private String requestMetadata;
  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON) private String providerMetadata;
}
