package com.poscloud.wallet.transaction;

import com.poscloud.wallet.common.BaseEntity;
import com.poscloud.wallet.common.Types.*;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "transaction_types")
public class TransactionType extends BaseEntity {
  private String code;
  private String name;
  private String category;
  private boolean isReversible;
  private boolean allowsFee;
  private boolean allowsCommission;

  @Enumerated(EnumType.STRING)
  private Status status;
}
