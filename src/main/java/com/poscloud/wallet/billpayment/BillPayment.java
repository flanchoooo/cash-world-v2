package com.poscloud.wallet.billpayment;

import com.poscloud.wallet.common.BaseEntity;
import com.poscloud.wallet.common.Types.*;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "bill_payments")
public class BillPayment extends BaseEntity {
  private String transactionReference;
  private UUID billerId;
  private UUID billerProductId;
  private String customerReference;
  private String providerReference;
  private String providerStatus;

  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
  private String requestData;

  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
  private String responseData;
}
