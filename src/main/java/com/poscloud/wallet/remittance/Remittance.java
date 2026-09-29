package com.poscloud.wallet.remittance;

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
@Table(name = "remittances")
public class Remittance extends BaseEntity {
  @Column(length = 50, nullable = false, unique = true)
  private String remittanceReference;

  @Column(length = 50, nullable = false, unique = true)
  private String transactionReference;

  @Column(length = 50, unique = true)
  private String payoutTransactionReference;
  private UUID senderCustomerId;
  private UUID receiverCustomerId;
  private UUID providerBillerId;
  private UUID sourceCurrencyId;
  private UUID destinationCurrencyId;
  private UUID createdByUserId;
  private UUID cashedOutByUserId;

  @Column(precision = 19, scale = 4)
  private BigDecimal sendAmount;

  @Column(precision = 19, scale = 6)
  private BigDecimal exchangeRate;

  @Column(precision = 19, scale = 4)
  private BigDecimal payoutAmount;

  @Column(precision = 19, scale = 4)
  private BigDecimal feeAmount;

  @Column(precision = 19, scale = 4)
  private BigDecimal destinationFeeAmount;

  private boolean feeOverridden;

  private String senderName;
  private String senderMobile;
  private String senderIdNumber;
  private String receiverName;
  private String receiverMobile;
  private String receiverIdNumber;
  private String providerReference;
  private String reasonForSending;
  private String sourceOfFunds;
  @Column(name = "collection_code", columnDefinition = "CHAR(6)")
  private String collectionCode;
  private String proofOfPaymentName;
  private String proofOfPaymentContentType;
  private String receiptShareToken;

  @Lob
  @Column(columnDefinition = "LONGBLOB")
  private byte[] proofOfPaymentData;

  @Enumerated(EnumType.STRING)
  private RemittanceStatus status;
}
